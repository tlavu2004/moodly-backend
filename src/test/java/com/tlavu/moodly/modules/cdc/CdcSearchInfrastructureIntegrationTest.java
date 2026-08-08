package com.tlavu.moodly.modules.cdc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.tlavu.moodly.modules.cdc.application.CdcDeliveryService;
import com.tlavu.moodly.modules.cdc.application.DailyEntrySearchDocument;
import com.tlavu.moodly.modules.cdc.application.DailyEntryReindexService;
import com.tlavu.moodly.modules.cdc.domain.CdcResumeToken;
import com.tlavu.moodly.modules.cdc.infrastructure.CdcResumeTokenRepository;
import com.tlavu.moodly.modules.cdc.infrastructure.DailyEntryChangeStreamListener;
import com.tlavu.moodly.modules.search.application.EntrySearchService;
import com.tlavu.moodly.modules.cdc.infrastructure.CdcDeadLetterRepository;
import com.tlavu.moodly.modules.entries.domain.DailyEntry;
import com.tlavu.moodly.modules.entries.infrastructure.DailyEntryRepository;
import com.tlavu.moodly.modules.search.infrastructure.DailyEntrySearchIndexManager;
import com.tlavu.moodly.support.ElasticsearchTestConfiguration;
import com.tlavu.moodly.support.MongoTestConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import com.tlavu.moodly.support.CdcSearchTestSupport;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest(properties = {
		"moodly.entries.collection-name=daily_entries_cdc_test",
		"moodly.cdc.enabled=true",
		"moodly.cdc.stream-id=daily_entries_cdc_test",
		"moodly.cdc.maintenance-key=cdc-test-maintenance-key",
		"moodly.cdc.retry.max-attempts=3",
		"moodly.cdc.retry.initial-backoff-ms=1",
		"moodly.cdc.reindex.batch-size=2",
		"moodly.search.index.name=daily_entries_search_cdc_test",
		"moodly.search.index.initialization-enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({MongoTestConfiguration.class, ElasticsearchTestConfiguration.class})
class CdcSearchInfrastructureIntegrationTest {

	@Autowired
	private ElasticsearchClient elasticsearchClient;
	@Autowired
	private DailyEntrySearchIndexManager indexManager;
	@Autowired
	private CdcSearchTestSupport cdcSearchTestSupport;
	@Autowired
	private DailyEntryRepository dailyEntryRepository;
	@Autowired
	private CdcDeadLetterRepository deadLetterRepository;
	@Autowired
	private CdcDeliveryService deliveryService;
	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private EntrySearchService entrySearchService;
	@Autowired
	private DailyEntryChangeStreamListener listener;
	@Autowired
	private CdcResumeTokenRepository resumeTokenRepository;
	@MockitoSpyBean
	private DailyEntryReindexService reindexService;

	@BeforeEach
	void prepareState() throws Exception {
		listener.stop();
		cdcSearchTestSupport.clearState();
		listener.start();
		awaitChangeStreamRegistration();
	}

	@AfterEach
	void cleanUp() throws Exception {
		listener.stop();
		cdcSearchTestSupport.clearState();
	}

	@Test
	void createsTheConfiguredSearchIndexAgainstTestcontainers() throws Exception {
		assertThat(elasticsearchClient.indices().exists(request -> request.index(indexManager.getIndexName())).value())
				.isTrue();
		var mapping = Objects.requireNonNull(
				elasticsearchClient.indices().getMapping(request -> request.index(indexManager.getIndexName()))
						.get(indexManager.getIndexName()),
				"Configured Elasticsearch index mapping must be present"
		);
		var properties = Objects.requireNonNull(mapping.mappings(), "Configured Elasticsearch mapping must be present").properties();
		assertThat(properties).containsKeys("userId", "date", "mood", "habits");
		assertThat(properties.get("userId").toString()).contains("\"type\":\"keyword\"");
		assertThat(properties.get("date").toString()).contains("\"type\":\"date\"");
		assertThat(properties.get("mood").toString())
				.contains("\"score\":{\"type\":\"integer\"}", "\"note\":{\"type\":\"text\"}", "\"tags\":{\"type\":\"text\"}");
		assertThat(properties.get("habits").toString()).contains("\"note\":{\"type\":\"text\"}");
	}

	@Test
	void synchronizesInsertUpdateAndDelete() throws Exception {
		var entry = entry(cdcSearchTestSupport.newUserId(), "first note", List.of("old"), "old habit", 2);
		entry = cdcSearchTestSupport.save(entry);
		cdcSearchTestSupport.awaitIndexed(entry.getId());
		var insertedDocument = document(entry.getId());
		assertThat(insertedDocument.userId()).isEqualTo(entry.getUserId());
		assertThat(insertedDocument.date()).isEqualTo(entry.getDate());
		assertThat(insertedDocument.mood().score()).isEqualTo(2);
		assertThat(insertedDocument.mood().note()).isEqualTo("first note");
		assertThat(insertedDocument.mood().tags()).containsExactly("old");
		assertThat(insertedDocument.habits()).extracting(DailyEntrySearchDocument.Habit::note).containsExactly("old habit");

		entry.setMood(new DailyEntry.Mood(5, List.of("new"), "updated note"));
		entry.setHabits(List.of(new DailyEntry.HabitLog("habit-1", true, "new habit")));
		cdcSearchTestSupport.save(entry);
		awaitDocument(entry.getId(), document -> "updated note".equals(document.mood().note()));
		assertThat(document(entry.getId())).satisfies(indexed -> {
			assertThat(indexed.mood().score()).isEqualTo(5);
			assertThat(indexed.mood().note()).isEqualTo("updated note").doesNotContain("first note");
			assertThat(indexed.mood().tags()).containsExactly("new").doesNotContain("old");
			assertThat(indexed.habits()).extracting(DailyEntrySearchDocument.Habit::note)
					.containsExactly("new habit").doesNotContain("old habit");
		});

		dailyEntryRepository.delete(entry);
		cdcSearchTestSupport.awaitRemoved(entry.getId());
	}

	@Test
	void duplicateDeliveryAndReindexRemainIdempotent() throws Exception {
		var first = cdcSearchTestSupport.save(entry(cdcSearchTestSupport.newUserId(), "repeatable", List.of("tag"), "habit", 3));
		deliveryService.deliverUpsert("duplicate-1", "insert", first);
		deliveryService.deliverUpsert("duplicate-1", "insert", first);
		awaitDocument(first.getId(), document -> "repeatable".equals(document.mood().note()));
		elasticsearchClient.indices().refresh(request -> request.index(indexManager.getIndexName()));
		assertThat(elasticsearchClient.count(request -> request.index(indexManager.getIndexName())).count()).isEqualTo(1);
		assertThat(deadLetterRepository.findAll()).isEmpty();

		var second = entry(first.getUserId(), "second", List.of(), "habit two", 4);
		second.setDate(first.getDate().plusDays(1));
		second = cdcSearchTestSupport.save(second);
		var result = reindexService.reindex();
		assertThat(result.indexedEntries()).isEqualTo(2);
		awaitDocument(second.getId(), document -> "second".equals(document.mood().note()));
		elasticsearchClient.indices().refresh(request -> request.index(indexManager.getIndexName()));
		assertThat(elasticsearchClient.count(request -> request.index(indexManager.getIndexName())).count()).isEqualTo(2);
		assertThat(document(first.getId()).mood().note()).isEqualTo("repeatable");
	}

	@Test
	void searchesOnlyTheRequestingUsersIndexedEntries() throws Exception {
		var user = cdcSearchTestSupport.newUserId();
		var own = cdcSearchTestSupport.save(entry(user, "I feel unique calm", List.of("calm"), "walk", 4));
		var other = cdcSearchTestSupport.save(entry(cdcSearchTestSupport.newUserId(), "I feel unique calm", List.of("calm"), "walk", 4));
		cdcSearchTestSupport.awaitIndexed(own.getId());
		cdcSearchTestSupport.awaitIndexed(other.getId());
		com.tlavu.moodly.support.AsyncTestAwaiter.until("search result for " + own.getId(),
				() -> entrySearchService.search(user, "unique", own.getDate(), own.getDate()).stream()
						.anyMatch(result -> own.getId().equals(result.entryId())), () -> "entryId=" + own.getId());

		mockMvc.perform(get("/entries/search").with(jwt().jwt(token -> token.subject(user))).param("q", "unique").param("from", own.getDate().toString()).param("to", own.getDate().toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].entryId").value(own.getId()))
				.andExpect(jsonPath("$.data[0].highlights['mood.note'][0]", containsString("<em>unique</em>")));

		mockMvc.perform(get("/entries/search").with(jwt().jwt(token -> token.subject(user))).param("q", "does-not-match"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
		mockMvc.perform(get("/entries/search").with(jwt().jwt(token -> token.subject(user))).param("q", "unique").param("from", own.getDate().toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
		mockMvc.perform(get("/entries/search").with(jwt().jwt(token -> token.subject(user))).param("q", "unique").param("to", own.getDate().toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
		mockMvc.perform(get("/entries/search").with(jwt().jwt(token -> token.subject(user))).param("q", "   "))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/entries/search").with(jwt().jwt(token -> token.subject(user))).param("q", "unique")
					.param("from", own.getDate().plusDays(1).toString()).param("to", own.getDate().toString()))
				.andExpect(status().isBadRequest());
	}

	@Test
	void resumesFromPersistedTokenAfterListenerRestart() throws Exception {
		var first = cdcSearchTestSupport.save(entry(cdcSearchTestSupport.newUserId(), "before restart", List.of(), "habit", 3));
		cdcSearchTestSupport.awaitIndexed(first.getId());
		var tokenBeforeRestart = resumeTokenRepository.findById("daily_entries_cdc_test")
				.map(CdcResumeToken::getToken).orElseThrow();
		clearInvocations(reindexService);

		listener.stop();
		listener.start();
		awaitChangeStreamRegistration();
		var second = cdcSearchTestSupport.save(entry(cdcSearchTestSupport.newUserId(), "after restart", List.of(), "habit", 4));

		cdcSearchTestSupport.awaitIndexed(second.getId());
		assertThat(resumeTokenRepository.findById("daily_entries_cdc_test").map(CdcResumeToken::getToken))
				.hasValueSatisfying(token -> assertThat(token).isNotEqualTo(tokenBeforeRestart));
		elasticsearchClient.indices().refresh(request -> request.index(indexManager.getIndexName()));
		assertThat(elasticsearchClient.count(request -> request.index(indexManager.getIndexName())).count()).isEqualTo(2);
		verify(reindexService, never()).reindex();
	}

	private DailyEntry entry(String userId, String note, List<String> tags, String habitNote, int score) {
		var entry = new DailyEntry(userId, LocalDate.of(2026, 8, 6));
		entry.setMood(new DailyEntry.Mood(score, tags, note));
		entry.setHabits(List.of(new DailyEntry.HabitLog("habit-1", true, habitNote)));
		return entry;
	}

	private DailyEntrySearchDocument document(String entryId) throws Exception {
		return elasticsearchClient.get(request -> request.index(indexManager.getIndexName()).id(entryId), DailyEntrySearchDocument.class).source();
	}

	private void awaitDocument(String entryId, java.util.function.Predicate<DailyEntrySearchDocument> predicate) throws Exception {
		com.tlavu.moodly.support.AsyncTestAwaiter.until("updated Elasticsearch document " + entryId,
				() -> predicate.test(document(entryId)), () -> "entryId=" + entryId);
	}

	/** Waits for the asynchronous driver subscription created by the listener container. */
	private void awaitChangeStreamRegistration() throws InterruptedException {
		TimeUnit.SECONDS.sleep(1);
	}
}
