package com.tlavu.moodly.modules.e2e.service.implementation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tlavu.moodly.modules.search.infrastructure.elasticsearch.ElasticsearchClient;
import com.tlavu.moodly.modules.entries.repository.DailyEntryRepository;
import com.tlavu.moodly.modules.e2e.controller.E2eResetController;
import com.tlavu.moodly.modules.habits.repository.HabitRepository;
import com.tlavu.moodly.modules.search.infrastructure.elasticsearch.DailyEntrySearchIndexManager;
import com.tlavu.moodly.modules.search.infrastructure.elasticsearch.DailyEntrySearchWriter;
import com.tlavu.moodly.modules.search.model.elasticsearch.DailyEntrySearchDocument;
import com.tlavu.moodly.shared.error.exception.common.SearchInfrastructureUnavailableException;
import com.tlavu.moodly.shared.controller.advice.GlobalExceptionHandler;
import java.util.List;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class E2eUserResetServiceTest {
	@Test
	@SuppressWarnings("unchecked")
	void treatsFoundSearchDocumentWithoutSourceAsUnverified() throws Exception {
		HabitRepository habits = mock(HabitRepository.class);
		DailyEntryRepository entries = mock(DailyEntryRepository.class);
		DailyEntrySearchWriter writer = mock(DailyEntrySearchWriter.class);
		ElasticsearchClient elasticsearch = mock(ElasticsearchClient.class);
		DailyEntrySearchIndexManager indexManager = mock(DailyEntrySearchIndexManager.class);
		when(indexManager.getIndexName()).thenReturn("e2e-test-index");
		when(habits.findByUserId("e2e-user")).thenReturn(List.of());

		E2eUserResetService service = new E2eUserResetService(
				habits, entries, writer, elasticsearch, indexManager, "e2e-user");

		assertThatThrownBy(service::reset)
				.isInstanceOf(SearchInfrastructureUnavailableException.class)
				.hasMessage("E2E reset search indexing could not be verified");

		MockMvc mvc = MockMvcBuilders.standaloneSetup(new E2eResetController(service, "test-token"))
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
		mvc.perform(post("/internal/e2e/reset").header("X-E2E-Reset-Token", "test-token"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.error.code").value("SEARCH_UNAVAILABLE"))
				.andExpect(jsonPath("$.error.message").value("E2E reset search indexing could not be verified"))
				.andExpect(jsonPath("$.error.path").value("/internal/e2e/reset"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void rejectsStaleSearchDocumentEvenWhenItBelongsToTheSyntheticUser() throws Exception {
		HabitRepository habits = mock(HabitRepository.class);
		DailyEntryRepository entries = mock(DailyEntryRepository.class);
		DailyEntrySearchWriter writer = mock(DailyEntrySearchWriter.class);
		ElasticsearchClient elasticsearch = mock(ElasticsearchClient.class);
		DailyEntrySearchIndexManager indexManager = mock(DailyEntrySearchIndexManager.class);
		when(indexManager.getIndexName()).thenReturn("e2e-test-index");
		when(elasticsearch.get(eq("e2e-test-index"), any(), eq(DailyEntrySearchDocument.class))).thenReturn(new DailyEntrySearchDocument("e2e-user", LocalDate.of(2026, 9, 8), null, List.of()));

		E2eUserResetService service = new E2eUserResetService(
				habits, entries, writer, elasticsearch, indexManager, "e2e-user");

		assertThatThrownBy(service::reset)
				.isInstanceOf(SearchInfrastructureUnavailableException.class)
				.hasMessage("E2E reset search indexing could not be verified");
	}
}
