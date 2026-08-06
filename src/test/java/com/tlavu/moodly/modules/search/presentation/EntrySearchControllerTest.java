package com.tlavu.moodly.modules.search.presentation;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tlavu.moodly.modules.search.application.EntrySearchService;
import com.tlavu.moodly.shared.application.exception.SearchInfrastructureUnavailableException;
import com.tlavu.moodly.shared.presentation.advice.GlobalExceptionHandler;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EntrySearchController.class)
@Import(GlobalExceptionHandler.class)
class EntrySearchControllerTest {

	private static final String USER_ID = "search-test-user";

	@Autowired
	private MockMvc mockMvc;
	@MockitoBean
	private EntrySearchService entrySearchService;

	@Test
	void searchesWithDateFiltersAndPassesTemporaryUserScopeToTheService() throws Exception {
		var from = LocalDate.of(2026, 8, 1);
		var to = LocalDate.of(2026, 8, 31);
		var result = new EntrySearchService.EntrySearchResult(
				"entry-1",
				LocalDate.of(2026, 8, 6),
				Map.of("mood.note", List.of("I felt <em>tired</em>."))
		);
		when(entrySearchService.search(USER_ID, "tired", from, to)).thenReturn(List.of(result));

		mockMvc.perform(get("/entries/search")
					.header("X-User-Id", USER_ID)
					.param("q", "  tired  ")
					.param("from", from.toString())
					.param("to", to.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data[0].entryId").value("entry-1"))
				.andExpect(jsonPath("$.data[0].highlights['mood.note'][0]").value("I felt <em>tired</em>."));

		verify(entrySearchService).search(USER_ID, "tired", from, to);
	}

	@Test
	void rejectsBlankQueryBeforeCallingTheSearchService() throws Exception {
		mockMvc.perform(get("/entries/search")
					.header("X-User-Id", USER_ID)
					.param("q", "   "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

		verifyNoInteractions(entrySearchService);
	}

	@Test
	void rejectsDateRangeWhenFromIsAfterToBeforeCallingTheSearchService() throws Exception {
		mockMvc.perform(get("/entries/search")
					.header("X-User-Id", USER_ID)
					.param("q", "tired")
					.param("from", "2026-08-31")
					.param("to", "2026-08-01"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

		verifyNoInteractions(entrySearchService);
	}

	@Test
	void returnsServiceUnavailableWhenElasticsearchCannotServeSearch() throws Exception {
		when(entrySearchService.search(USER_ID, "tired", null, null))
				.thenThrow(new SearchInfrastructureUnavailableException("Elasticsearch search is unavailable", new java.io.IOException("down")));

		mockMvc.perform(get("/entries/search")
					.header("X-User-Id", USER_ID)
					.param("q", "tired"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("SEARCH_UNAVAILABLE"));
	}
}
