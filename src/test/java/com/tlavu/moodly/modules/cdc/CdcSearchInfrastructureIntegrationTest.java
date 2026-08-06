package com.tlavu.moodly.modules.cdc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.tlavu.moodly.modules.cdc.application.CdcDeliveryService;
import com.tlavu.moodly.modules.cdc.application.DailyEntrySearchDocument;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import com.tlavu.moodly.support.CdcSearchTestSupport;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "cdc-test"})
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

	@AfterEach
	void cleanUp() throws Exception {
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
	}

	@Test
	void synchronizesInsertUpdateAndDelete() throws Exception {
		var entry = entry(cdcSearchTestSupport.newUserId(), "first note", List.of("old"), "old habit", 2);
		entry = cdcSearchTestSupport.save(entry);
		cdcSearchTestSupport.awaitIndexed(entry.getId());
		assertThat(document(entry.getId()).mood().note()).isEqualTo("first note");

		entry.setMood(new DailyEntry.Mood(5, List.of("new"), "updated note"));
		entry.setHabits(List.of(new DailyEntry.HabitLog("habit-1", true, "new habit")));
		cdcSearchTestSupport.save(entry);
		awaitDocument(entry.getId(), document -> "updated note".equals(document.mood().note()));
		assertThat(document(entry.getId()).mood().tags()).containsExactly("new");
		assertThat(document(entry.getId()).habits()).extracting(DailyEntrySearchDocument.Habit::note).containsExactly("new habit");

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
		var result = new com.tlavu.moodly.modules.cdc.application.DailyEntryReindexService(dailyEntryRepository,
				new com.tlavu.moodly.modules.cdc.application.DailyEntrySearchWriter(elasticsearchClient, indexManager), indexManager, 2).reindex();
		assertThat(result.indexedEntries()).isEqualTo(2);
		awaitDocument(second.getId(), document -> "second".equals(document.mood().note()));
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

		mockMvc.perform(get("/entries/search").header("X-User-Id", user).param("q", "unique").param("from", own.getDate().toString()).param("to", own.getDate().toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].entryId").value(own.getId()));
	}

	@Test
	void resumesFromPersistedTokenAfterListenerRestart() throws Exception {
		var first = cdcSearchTestSupport.save(entry(cdcSearchTestSupport.newUserId(), "before restart", List.of(), "habit", 3));
		cdcSearchTestSupport.awaitIndexed(first.getId());
		var tokenBeforeRestart = resumeTokenRepository.findById("daily_entries_cdc_test")
				.map(CdcResumeToken::getToken).orElseThrow();

		listener.stop();
		var second = cdcSearchTestSupport.save(entry(cdcSearchTestSupport.newUserId(), "after restart", List.of(), "habit", 4));
		listener.start();

		cdcSearchTestSupport.awaitIndexed(second.getId());
		assertThat(resumeTokenRepository.findById("daily_entries_cdc_test").map(CdcResumeToken::getToken))
				.hasValueSatisfying(token -> assertThat(token).isNotEqualTo(tokenBeforeRestart));
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
}
