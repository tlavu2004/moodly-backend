package com.tlavu.moodly.modules.e2e.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tlavu.moodly.modules.search.infrastructure.elasticsearch.ElasticsearchClient;
import com.tlavu.moodly.modules.search.infrastructure.elasticsearch.ElasticsearchException;
import com.tlavu.moodly.modules.entries.model.DailyEntry;
import com.tlavu.moodly.modules.entries.model.HabitLog;
import com.tlavu.moodly.modules.entries.model.Mood;
import com.tlavu.moodly.modules.entries.repository.DailyEntryRepository;
import com.tlavu.moodly.modules.habits.domain.TargetFrequency;
import com.tlavu.moodly.modules.habits.model.Habit;
import com.tlavu.moodly.modules.habits.repository.HabitRepository;
import com.tlavu.moodly.modules.search.infrastructure.elasticsearch.DailyEntrySearchIndexManager;
import com.tlavu.moodly.modules.search.infrastructure.elasticsearch.DailyEntrySearchWriter;
import com.tlavu.moodly.modules.search.listener.DailyEntryChangeStreamListener;
import com.tlavu.moodly.modules.search.model.elasticsearch.DailyEntrySearchDocument;
import com.tlavu.moodly.support.CdcSearchTestSupport;
import com.tlavu.moodly.support.ElasticsearchTestConfiguration;
import com.tlavu.moodly.support.MongoTestConfiguration;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"moodly.entries.collection-name=daily_entries_e2e_test",
		"moodly.cdc.enabled=true",
		"moodly.cdc.stream-id=daily_entries_e2e_test",
		"moodly.cdc.maintenance-key=cdc-test-maintenance-key",
		"moodly.cdc.retry.max-attempts=3",
		"moodly.cdc.retry.initial-backoff-ms=1",
		"moodly.cdc.reindex.batch-size=2",
		"moodly.search.index.name=daily_entries_search_e2e_test",
		"moodly.e2e.reset.enabled=true",
		"moodly.e2e.reset.token=test-e2e-reset-token",
		"moodly.e2e.reset.user-id=synthetic|moodly-playwright-e2e",
		"moodly.search.index.initialization-enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({MongoTestConfiguration.class, ElasticsearchTestConfiguration.class})
class E2eResetIntegrationTest {

	private static final String TEST_E2E_USER_ID = "synthetic|moodly-playwright-e2e";

	@Autowired
	private CdcSearchTestSupport cdcSearchTestSupport;
	@Autowired
	private DailyEntryRepository dailyEntryRepository;
	@Autowired
	private HabitRepository habitRepository;
	@MockitoSpyBean
	private ElasticsearchClient elasticsearchClient;
	@Autowired
	private DailyEntrySearchIndexManager indexManager;
	@Autowired
	private DailyEntryChangeStreamListener listener;
	@Autowired
	private MockMvc mockMvc;
	@MockitoSpyBean
	private DailyEntrySearchWriter searchWriter;

	@BeforeEach
	void prepareState() throws Exception {
		listener.stop();
		cdcSearchTestSupport.clearState();
		listener.start();
		assertThat(listener.awaitRegistration(Duration.ofSeconds(10))).isTrue();
	}

	@AfterEach
	void cleanUp() throws Exception {
		listener.stop();
		cdcSearchTestSupport.clearState();
	}

	@Test
	void resetEndpointRequiresItsTokenAndLeavesOtherUsersDataUntouched() throws Exception {
		String target = TEST_E2E_USER_ID;
		String other = "synthetic|another-e2e-account";
		Habit targetHabit = habitRepository.save(new Habit("before-reset-target", target, "Old target habit", null, TargetFrequency.DAILY, true));
		Habit otherHabit = habitRepository.save(new Habit("other-user-habit", other, "Other user habit", null, TargetFrequency.DAILY, true));
		DailyEntry targetEntry = cdcSearchTestSupport.save(entry(target, "old target marker", List.of(), "old note", 2));
		DailyEntry otherEntry = cdcSearchTestSupport.save(entry(other, "other user marker", List.of(), "private note", 3));
		cdcSearchTestSupport.awaitIndexed(targetEntry.getId());
		cdcSearchTestSupport.awaitIndexed(otherEntry.getId());

		mockMvc.perform(post("/internal/e2e/reset"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("E2E_RESET_FORBIDDEN"));
		mockMvc.perform(post("/internal/e2e/reset").header("X-E2E-Reset-Token", "wrong"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("E2E_RESET_FORBIDDEN"));
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
	void refreshesAndRetriesResetAfterElasticsearchConflict() throws Exception {
		listener.stop();
		doThrow(new ElasticsearchException(409)).doCallRealMethod()
				.when(elasticsearchClient).request(eq("POST"), eq(deleteByUserPath()), any());

		mockMvc.perform(post("/internal/e2e/reset").header("X-E2E-Reset-Token", "test-e2e-reset-token"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.searchIndexing").value("verified"));

		assertDeleteRetriedAfterRefresh();
	}

	@Test
	void returnsServiceUnavailableWhenElasticsearchConflictPersists() throws Exception {
		listener.stop();
		doThrow(new ElasticsearchException(409))
				.when(elasticsearchClient).request(eq("POST"), eq(deleteByUserPath()), any());

		mockMvc.perform(post("/internal/e2e/reset").header("X-E2E-Reset-Token", "test-e2e-reset-token"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.error.code").value("SEARCH_UNAVAILABLE"));

		assertDeleteRetriedAfterRefresh();
	}

	private String deleteByUserPath() {
		return "/" + indexManager.getIndexName() + "/_delete_by_query?refresh=true";
	}

	private void assertDeleteRetriedAfterRefresh() throws IOException {
		var order = inOrder(elasticsearchClient);
		order.verify(elasticsearchClient).request(eq("POST"), eq(deleteByUserPath()), any());
		order.verify(elasticsearchClient).refresh(indexManager.getIndexName());
		order.verify(elasticsearchClient).request(eq("POST"), eq(deleteByUserPath()), any());
		verify(elasticsearchClient, times(2))
				.request(eq("POST"), eq(deleteByUserPath()), any());
	}


	@Test
	void returnsServiceUnavailableWhenIndexingFails() throws Exception {
		listener.stop();
		doThrow(new IOException("connection refused"))
				.when(searchWriter).index(any(DailyEntry.class));

		mockMvc.perform(post("/internal/e2e/reset").header("X-E2E-Reset-Token", "test-e2e-reset-token"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("SEARCH_UNAVAILABLE"))
				.andExpect(jsonPath("$.error.message").value("E2E reset search indexing is unavailable"));
	}

	@Test
	void returnsServiceUnavailableWhenSeededDocumentsCannotBeVerified() throws Exception {
		listener.stop();
		doNothing().when(searchWriter).index(any(DailyEntry.class));

		mockMvc.perform(post("/internal/e2e/reset").header("X-E2E-Reset-Token", "test-e2e-reset-token"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("SEARCH_UNAVAILABLE"))
				.andExpect(jsonPath("$.error.message").value("E2E reset search indexing could not be verified"));
	}

	private DailyEntry entry(String userId, String note, List<String> tags, String habitNote, int score) {
		DailyEntry entry = new DailyEntry(userId, LocalDate.of(2026, 8, 6));
		entry.setMood(new Mood(score, tags, note));
		entry.setHabits(List.of(new HabitLog("habit-1", true, habitNote)));
		return entry;
	}

	private DailyEntrySearchDocument document(String entryId) throws Exception {
		return elasticsearchClient.get(indexManager.getIndexName(), entryId, DailyEntrySearchDocument.class);
	}
}
