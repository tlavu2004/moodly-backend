package com.tlavu.moodly.modules.search.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch.indices.get_mapping.IndexMappingRecord;
import com.tlavu.moodly.modules.search.service.indexing.CdcDeliveryService;
import com.tlavu.moodly.modules.search.monitoring.CdcHealthIndicator;
import com.tlavu.moodly.modules.search.monitoring.CdcMonitor;
import com.tlavu.moodly.modules.search.model.mongodb.CdcDeadLetter;
import com.tlavu.moodly.modules.search.model.elasticsearch.DailyEntrySearchDocument;
import com.tlavu.moodly.modules.search.model.elasticsearch.IndexedHabitLog;
import com.tlavu.moodly.modules.search.service.indexing.DailyEntryReindexService;
import com.tlavu.moodly.modules.search.model.mongodb.CdcResumeToken;
import com.tlavu.moodly.modules.search.repository.CdcResumeTokenRepository;
import com.tlavu.moodly.modules.search.listener.DailyEntryChangeStreamListener;
import com.tlavu.moodly.modules.search.service.implementation.EntrySearchService;
import com.tlavu.moodly.modules.search.repository.CdcDeadLetterRepository;
import com.tlavu.moodly.modules.entries.model.DailyEntry;
import com.tlavu.moodly.modules.entries.model.HabitLog;
import com.tlavu.moodly.modules.entries.model.Mood;
import com.tlavu.moodly.modules.entries.repository.DailyEntryRepository;
import com.tlavu.moodly.modules.habits.model.Habit;
import com.tlavu.moodly.modules.habits.domain.TargetFrequency;
import com.tlavu.moodly.modules.habits.repository.HabitRepository;
import com.tlavu.moodly.modules.e2e.service.E2eUserResetService;
import com.tlavu.moodly.modules.search.infrastructure.elasticsearch.DailyEntrySearchIndexManager;
import com.tlavu.moodly.modules.search.service.input.EntrySearchInput;
import com.tlavu.moodly.modules.search.service.view.ReindexView;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
		"moodly.e2e.reset.enabled=true",
		"moodly.e2e.reset.token=test-e2e-reset-token",
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
	private HabitRepository habitRepository;
	@Autowired
	private CdcDeadLetterRepository deadLetterRepository;
	@Autowired
	private CdcDeliveryService deliveryService;
	@Autowired
	private CdcHealthIndicator cdcHealthIndicator;
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
	void healthCountsPersistedDeadLettersAfterFailedReplayAndNewMonitorInstance() {
		assertThat(cdcHealthIndicator.health().getDetails())
				.containsEntry("failedEventCount", 0L);

		CdcDeadLetter deadLetter = deadLetterRepository.save(new CdcDeadLetter(
				"event-1", "update", "entry-1", "invalid payload", "old failure", 1, Instant.now()
		));
		assertThat(cdcHealthIndicator.health().getDetails())
				.containsEntry("failedEventCount", 1L);

		assertThatThrownBy(() -> deliveryService.replay(deadLetter.getId()))
				.isInstanceOf(IllegalStateException.class);
		assertThat(cdcHealthIndicator.health().getDetails())
				.containsEntry("failedEventCount", 1L);

		CdcHealthIndicator restartedIndicator = new CdcHealthIndicator(new CdcMonitor(), deadLetterRepository);
		assertThat(restartedIndicator.health().getDetails())
				.containsEntry("failedEventCount", 1L);

		deadLetterRepository.delete(deadLetter);
		assertThat(restartedIndicator.health().getDetails())
				.containsEntry("failedEventCount", 0L);
	}

	@Test
	void resetEndpointRequiresItsTokenAndLeavesOtherUsersDataUntouched() throws Exception {
		String target = E2eUserResetService.DEFAULT_USER_ID;
		String other = "synthetic|another-e2e-account";
		Habit targetHabit = habitRepository.save(new Habit("before-reset-target", target, "Old target habit", null, TargetFrequency.DAILY, true));
		Habit otherHabit = habitRepository.save(new Habit("other-user-habit", other, "Other user habit", null, TargetFrequency.DAILY, true));
		DailyEntry targetEntry = cdcSearchTestSupport.save(entry(target, "old target marker", List.of(), "old note", 2));
		DailyEntry otherEntry = cdcSearchTestSupport.save(entry(other, "other user marker", List.of(), "private note", 3));
		cdcSearchTestSupport.awaitIndexed(targetEntry.getId());
		cdcSearchTestSupport.awaitIndexed(otherEntry.getId());

		mockMvc.perform(post("/internal/e2e/reset"))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/internal/e2e/reset").header("X-E2E-Reset-Token", "wrong"))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/internal/e2e/reset").header("Authorization", "Bearer wrong"))
				.andExpect(status().isUnauthorized());
		assertThat(habitRepository.findById(targetHabit.getId())).isPresent();
		assertThat(dailyEntryRepository.findById(targetEntry.getId())).isPresent();

		mockMvc.perform(post("/internal/e2e/reset").header("X-E2E-Reset-Token", "test-e2e-reset-token"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.userId").value(target))
				.andExpect(jsonPath("$.data.habits").value(2))
				.andExpect(jsonPath("$.data.entries").value(3))
				.andExpect(jsonPath("$.data.searchIndexing").value("verified"));
		assertThat(habitRepository.findById(otherHabit.getId())).isPresent();
		assertThat(dailyEntryRepository.findById(otherEntry.getId())).isPresent();
		assertThat(document(otherEntry.getId()).userId()).isEqualTo(other);
		assertThat(dailyEntryRepository.findByUserId(target)).hasSize(3);
		assertThat(habitRepository.findByUserId(target)).hasSize(2);

		List<String> baselineEntryIds = dailyEntryRepository.findByUserId(target).stream().map(DailyEntry::getId).sorted().toList();
		List<String> baselineHabitIds = habitRepository.findByUserId(target).stream().map(Habit::getId).sorted().toList();
		mockMvc.perform(post("/internal/e2e/reset").header("X-E2E-Reset-Token", "test-e2e-reset-token"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.habits").value(2))
				.andExpect(jsonPath("$.data.entries").value(3));
		assertThat(dailyEntryRepository.findByUserId(target).stream().map(DailyEntry::getId).sorted().toList()).isEqualTo(baselineEntryIds);
		assertThat(habitRepository.findByUserId(target).stream().map(Habit::getId).sorted().toList()).isEqualTo(baselineHabitIds);
		assertThat(dailyEntryRepository.findById(otherEntry.getId())).isPresent();

		DailyEntry postReset = entry(other, "cdc after reset", List.of(), "walk", 4);
		postReset.setDate(otherEntry.getDate().plusDays(1));
		postReset = cdcSearchTestSupport.save(postReset);
		cdcSearchTestSupport.awaitIndexed(postReset.getId());
	}

	@Test
	void createsTheConfiguredSearchIndexAgainstTestcontainers() throws Exception {
		assertThat(elasticsearchClient.indices().exists(request -> request.index(indexManager.getIndexName())).value())
				.isTrue();
		IndexMappingRecord mapping = Objects.requireNonNull(
				elasticsearchClient.indices().getMapping(request -> request.index(indexManager.getIndexName()))
						.get(indexManager.getIndexName()),
				"Configured Elasticsearch index mapping must be present"
		);
		Map<String, Property> properties = Objects.requireNonNull(mapping.mappings(), "Configured Elasticsearch mapping must be present").properties();
		assertThat(properties).containsKeys("userId", "date", "mood", "habits");
		assertThat(properties.get("userId").toString()).contains("\"type\":\"keyword\"");
		assertThat(properties.get("date").toString()).contains("\"type\":\"date\"");
		assertThat(properties.get("mood").toString())
				.contains("\"score\":{\"type\":\"integer\"}", "\"note\":{\"type\":\"text\"}", "\"tags\":{\"type\":\"text\"}");
		assertThat(properties.get("habits").toString()).contains("\"note\":{\"type\":\"text\"}");
	}

	@Test
	void synchronizesInsertUpdateAndDelete() throws Exception {
		DailyEntry entry = entry(cdcSearchTestSupport.newUserId(), "first note", List.of("old"), "old habit", 2);
		entry = cdcSearchTestSupport.save(entry);
		cdcSearchTestSupport.awaitIndexed(entry.getId());
		DailyEntrySearchDocument insertedDocument = document(entry.getId());
		assertThat(insertedDocument.userId()).isEqualTo(entry.getUserId());
		assertThat(insertedDocument.date()).isEqualTo(entry.getDate());
		assertThat(insertedDocument.mood().score()).isEqualTo(2);
		assertThat(insertedDocument.mood().note()).isEqualTo("first note");
		assertThat(insertedDocument.mood().tags()).containsExactly("old");
		assertThat(insertedDocument.habits()).extracting(IndexedHabitLog::note).containsExactly("old habit");

		entry.setMood(new Mood(5, List.of("new"), "updated note"));
		entry.setHabits(List.of(new HabitLog("habit-1", true, "new habit")));
		cdcSearchTestSupport.save(entry);
		awaitDocument(entry.getId(), document -> "updated note".equals(document.mood().note()));
		assertThat(document(entry.getId())).satisfies(indexed -> {
			assertThat(indexed.mood().score()).isEqualTo(5);
			assertThat(indexed.mood().note()).isEqualTo("updated note").doesNotContain("first note");
			assertThat(indexed.mood().tags()).containsExactly("new").doesNotContain("old");
			assertThat(indexed.habits()).extracting(IndexedHabitLog::note)
					.containsExactly("new habit").doesNotContain("old habit");
		});

		dailyEntryRepository.delete(entry);
		cdcSearchTestSupport.awaitRemoved(entry.getId());
	}

	@Test
	void duplicateDeliveryAndReindexRemainIdempotent() throws Exception {
		DailyEntry first = cdcSearchTestSupport.save(entry(cdcSearchTestSupport.newUserId(), "repeatable", List.of("tag"), "habit", 3));
		deliveryService.deliverUpsert("duplicate-1", "insert", first);
		deliveryService.deliverUpsert("duplicate-1", "insert", first);
		awaitDocument(first.getId(), document -> "repeatable".equals(document.mood().note()));
		elasticsearchClient.indices().refresh(request -> request.index(indexManager.getIndexName()));
		assertThat(elasticsearchClient.count(request -> request.index(indexManager.getIndexName())).count()).isEqualTo(1);
		assertThat(deadLetterRepository.findAll()).isEmpty();

		DailyEntry second = entry(first.getUserId(), "second", List.of(), "habit two", 4);
		second.setDate(first.getDate().plusDays(1));
		second = cdcSearchTestSupport.save(second);
		ReindexView result = reindexService.reindex();
		assertThat(result.indexedEntries()).isEqualTo(2);
		awaitDocument(second.getId(), document -> "second".equals(document.mood().note()));
		elasticsearchClient.indices().refresh(request -> request.index(indexManager.getIndexName()));
		assertThat(elasticsearchClient.count(request -> request.index(indexManager.getIndexName())).count()).isEqualTo(2);
		assertThat(document(first.getId()).mood().note()).isEqualTo("repeatable");
	}

	@Test
	void searchesOnlyTheRequestingUsersIndexedEntries() throws Exception {
		String user = cdcSearchTestSupport.newUserId();
		DailyEntry own = cdcSearchTestSupport.save(entry(user, "I feel unique calm", List.of("calm"), "walk", 4));
		DailyEntry other = cdcSearchTestSupport.save(entry(cdcSearchTestSupport.newUserId(), "I feel unique calm", List.of("calm"), "walk", 4));
		cdcSearchTestSupport.awaitIndexed(own.getId());
		cdcSearchTestSupport.awaitIndexed(other.getId());
		com.tlavu.moodly.support.AsyncTestAwaiter.until("search result for " + own.getId(),
				() -> entrySearchService.search(user, new EntrySearchInput(
						"unique", own.getDate(), own.getDate(), 0, 50
				)).items().stream()
						.anyMatch(result -> own.getId().equals(result.entryId())), () -> "entryId=" + own.getId());

		mockMvc.perform(get("/entries/search").with(jwt().jwt(token -> token.subject(user))).param("q", "unique").param("from", own.getDate().toString()).param("to", own.getDate().toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.items[0].entryId").value(own.getId()))
				.andExpect(jsonPath("$.data.items[0].highlights['mood.note'][0].text", containsString("unique")))
				.andExpect(jsonPath("$.data.items[0].highlights['mood.note'][0].ranges[0].start").isNumber());

		mockMvc.perform(get("/entries/search").with(jwt().jwt(token -> token.subject(user))).param("q", "does-not-match"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0));
		mockMvc.perform(get("/entries/search").with(jwt().jwt(token -> token.subject(user))).param("q", "unique").param("from", own.getDate().toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1));
		mockMvc.perform(get("/entries/search").with(jwt().jwt(token -> token.subject(user))).param("q", "unique").param("to", own.getDate().toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1));
		mockMvc.perform(get("/entries/search").with(jwt().jwt(token -> token.subject(user))).param("q", "   "))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/entries/search").with(jwt().jwt(token -> token.subject(user))).param("q", "unique")
					.param("from", own.getDate().plusDays(1).toString()).param("to", own.getDate().toString()))
				.andExpect(status().isBadRequest());
	}

	@Test
	void paginatesSearchResultsAndReportsTheFullMatchCount() throws Exception {
		String user = cdcSearchTestSupport.newUserId();
		DailyEntry older = cdcSearchTestSupport.save(entry(user, "pagination marker older", List.of(), "walk", 3));
		DailyEntry newer = entry(user, "pagination marker newer", List.of(), "walk", 4);
		newer.setDate(older.getDate().plusDays(1));
		newer = cdcSearchTestSupport.save(newer);
		cdcSearchTestSupport.awaitIndexed(older.getId());
		cdcSearchTestSupport.awaitIndexed(newer.getId());
		com.tlavu.moodly.support.AsyncTestAwaiter.until("both search results to become visible",
				() -> entrySearchService.search(user, new EntrySearchInput("pagination", null, null, 0, 1)).totalElements() == 2,
				() -> "userId=" + user);

		mockMvc.perform(get("/entries/search").with(jwt().jwt(token -> token.subject(user)))
				.param("q", "pagination").param("page", "0").param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.items[0].entryId").value(newer.getId()))
				.andExpect(jsonPath("$.data.page").value(0))
				.andExpect(jsonPath("$.data.size").value(1))
				.andExpect(jsonPath("$.data.totalElements").value(2))
				.andExpect(jsonPath("$.data.totalPages").value(2))
				.andExpect(jsonPath("$.data.hasNext").value(true));
		mockMvc.perform(get("/entries/search").with(jwt().jwt(token -> token.subject(user)))
				.param("q", "pagination").param("page", "1").param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.items[0].entryId").value(older.getId()))
				.andExpect(jsonPath("$.data.page").value(1))
				.andExpect(jsonPath("$.data.totalElements").value(2))
				.andExpect(jsonPath("$.data.totalPages").value(2))
				.andExpect(jsonPath("$.data.hasNext").value(false));
	}

	@Test
	void resumesFromPersistedTokenAfterListenerRestart() throws Exception {
		DailyEntry first = cdcSearchTestSupport.save(entry(cdcSearchTestSupport.newUserId(), "before restart", List.of(), "habit", 3));
		cdcSearchTestSupport.awaitIndexed(first.getId());
		String tokenBeforeRestart = awaitResumeToken();
		clearInvocations(reindexService);

		listener.stop();
		listener.start();
		awaitChangeStreamRegistration();
		DailyEntry second = cdcSearchTestSupport.save(entry(cdcSearchTestSupport.newUserId(), "after restart", List.of(), "habit", 4));

		cdcSearchTestSupport.awaitIndexed(second.getId());
		assertThat(resumeTokenRepository.findById("daily_entries_cdc_test").map(CdcResumeToken::getToken))
				.hasValueSatisfying(token -> assertThat(token).isNotEqualTo(tokenBeforeRestart));
		elasticsearchClient.indices().refresh(request -> request.index(indexManager.getIndexName()));
		assertThat(elasticsearchClient.count(request -> request.index(indexManager.getIndexName())).count()).isEqualTo(2);
		verify(reindexService, never()).reindex();
	}

	private DailyEntry entry(String userId, String note, List<String> tags, String habitNote, int score) {
		DailyEntry entry = new DailyEntry(userId, LocalDate.of(2026, 8, 6));
		entry.setMood(new Mood(score, tags, note));
		entry.setHabits(List.of(new HabitLog("habit-1", true, habitNote)));
		return entry;
	}

	private DailyEntrySearchDocument document(String entryId) throws Exception {
		return elasticsearchClient.get(request -> request.index(indexManager.getIndexName()).id(entryId), DailyEntrySearchDocument.class).source();
	}

	private void awaitDocument(String entryId, java.util.function.Predicate<DailyEntrySearchDocument> predicate) throws Exception {
		com.tlavu.moodly.support.AsyncTestAwaiter.until("updated Elasticsearch document " + entryId,
				() -> predicate.test(document(entryId)), () -> "entryId=" + entryId);
	}

	private String awaitResumeToken() throws Exception {
		com.tlavu.moodly.support.AsyncTestAwaiter.until(
				"CDC resume token to be persisted",
				() -> resumeTokenRepository.findById("daily_entries_cdc_test").isPresent(),
				() -> "streamId=daily_entries_cdc_test"
		);
		return resumeTokenRepository.findById("daily_entries_cdc_test")
				.map(CdcResumeToken::getToken)
				.orElseThrow();
	}

	/** Waits for the asynchronous driver subscription created by the listener container. */
	private void awaitChangeStreamRegistration() throws InterruptedException {
		assertThat(listener.awaitRegistration(Duration.ofSeconds(10)))
				.as("CDC change-stream subscription should become active")
				.isTrue();
	}
}
