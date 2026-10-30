package com.tlavu.moodly.modules.search.controller.client;

import com.tlavu.moodly.modules.search.service.query.EntrySearchService;
import com.tlavu.moodly.modules.search.dto.request.EntrySearchRequest;
import com.tlavu.moodly.modules.search.dto.response.EntrySearchResponse;
import com.tlavu.moodly.modules.search.service.input.EntrySearchInput;
import com.tlavu.moodly.modules.search.service.view.EntrySearchPageView;
import com.tlavu.moodly.modules.auth.security.context.CurrentUser;
import com.tlavu.moodly.shared.controller.dto.response.ApiResponse;
import com.tlavu.moodly.shared.controller.dto.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/entries/search")
@Tag(name = "Entry search", description = "Full-text search over the authenticated user's daily entries")
public class EntrySearchController {

	private final EntrySearchService entrySearchService;
	private final CurrentUser currentUser;

	public EntrySearchController(EntrySearchService entrySearchService, CurrentUser currentUser) {
		this.entrySearchService = entrySearchService;
		this.currentUser = currentUser;
	}

	@GetMapping
	@Operation(summary = "Search daily entries", description = "Searches the authenticated user's entries by text, optionally limited to an inclusive date range.")
	public ApiResponse<PageResponse<EntrySearchResponse>> search(
			@RequestParam String q,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		EntrySearchRequest request = new EntrySearchRequest(q, from, to, page, size);
		EntrySearchInput input = new EntrySearchInput(
				request.q(), request.from(), request.to(), request.page(), request.size()
		);
		EntrySearchPageView view = entrySearchService.search(currentUser.id(), input);
		List<EntrySearchResponse> items = view.items().stream().map(EntrySearchResponse::from).toList();
		return ApiResponse.success(PageResponse.of(items, view.page(), view.size(), view.totalElements()));
	}
}
