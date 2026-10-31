package com.tlavu.moodly.modules.search.controller.client;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tlavu.moodly.modules.search.service.implementation.EntrySearchService;
import com.tlavu.moodly.modules.search.service.input.EntrySearchInput;
import com.tlavu.moodly.modules.search.service.view.EntrySearchPageView;
import com.tlavu.moodly.modules.search.service.view.EntrySearchView;
import com.tlavu.moodly.modules.auth.security.context.CurrentUser;
import com.tlavu.moodly.shared.error.exception.common.SearchInfrastructureUnavailableException;
import com.tlavu.moodly.shared.controller.advice.GlobalExceptionHandler;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EntrySearchController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(GlobalExceptionHandler.class)
class EntrySearchControllerTest {

	private static final String USER_ID = "search-test-user";

	@Autowired
	private MockMvc mockMvc;
	@MockitoBean
	private EntrySearchService entrySearchService;
	@MockitoBean
	private CurrentUser currentUser;

	@BeforeEach
	void setUpUser() {
		when(currentUser.id()).thenReturn(USER_ID);
	}

	@Test
	void searchesWithDateFiltersAndPassesAuthenticatedUserScopeToTheService() throws Exception {
		LocalDate from = LocalDate.of(2026, 8, 1);
		LocalDate to = LocalDate.of(2026, 8, 31);
		EntrySearchView result = new EntrySearchView(
				"entry-1",
				LocalDate.of(2026, 8, 6),
				Map.of("mood.note", List.of(new EntrySearchView.HighlightFragment(
						"I felt tired.", List.of(new EntrySearchView.HighlightRange(7, 12)))))
		);
		EntrySearchInput input = new EntrySearchInput("tired", from, to, 0, 20);
		when(entrySearchService.search(USER_ID, input))
				.thenReturn(new EntrySearchPageView(List.of(result), 0, 20, 1));

		mockMvc.perform(get("/entries/search")
					.param("q", "  tired  ")
					.param("from", from.toString())
					.param("to", to.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.items[0].entryId").value("entry-1"))
				.andExpect(jsonPath("$.data.items[0].highlights['mood.note'][0].text").value("I felt tired."))
				.andExpect(jsonPath("$.data.items[0].highlights['mood.note'][0].ranges[0].start").value(7))
				.andExpect(jsonPath("$.data.items[0].highlights['mood.note'][0].ranges[0].end").value(12))
				.andExpect(jsonPath("$.data.totalElements").value(1));

		verify(entrySearchService).search(USER_ID, input);
	}

	@Test
	void rejectsBlankQueryBeforeCallingTheSearchService() throws Exception {
		mockMvc.perform(get("/entries/search")
					.param("q", "   "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

		verifyNoInteractions(entrySearchService);
	}

	@Test
	void rejectsDateRangeWhenFromIsAfterToBeforeCallingTheSearchService() throws Exception {
		mockMvc.perform(get("/entries/search")
					.param("q", "tired")
					.param("from", "2026-08-31")
					.param("to", "2026-08-01"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

		verifyNoInteractions(entrySearchService);
	}

	@Test
	void returnsServiceUnavailableWhenElasticsearchCannotServeSearch() throws Exception {
		when(entrySearchService.search(USER_ID, new EntrySearchInput("tired", null, null, 0, 20)))
				.thenThrow(SearchInfrastructureUnavailableException.duringSearch(new java.io.IOException("down")));

		mockMvc.perform(get("/entries/search")
					.param("q", "tired"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("SEARCH_UNAVAILABLE"));
	}

	@Test
	void rejectsPageSizesAboveTheSharedMaximum() throws Exception {
		mockMvc.perform(get("/entries/search").param("q", "tired").param("size", "101"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
		verifyNoInteractions(entrySearchService);
	}

	@Test
	void rejectsPaginationThatExceedsTheSearchResultWindowWithoutCallingElasticsearch() throws Exception {
		mockMvc.perform(get("/entries/search")
					.param("q", "tired")
					.param("page", Integer.toString(Integer.MAX_VALUE))
					.param("size", "100"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
		verifyNoInteractions(entrySearchService);
	}

	@Test
	void rejectsQueriesLongerThanTwoHundredCharacters() throws Exception {
		mockMvc.perform(get("/entries/search").param("q", "x".repeat(201)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
		verifyNoInteractions(entrySearchService);
	}
}
