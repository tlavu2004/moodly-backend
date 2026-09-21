package com.tlavu.moodly.modules.search.presentation;

import com.tlavu.moodly.modules.search.application.EntrySearchService;
import com.tlavu.moodly.modules.auth.application.CurrentUser;
import com.tlavu.moodly.shared.presentation.dto.response.ApiResponse;
import com.tlavu.moodly.shared.presentation.dto.response.PageResponse;
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
	public ApiResponse<PageResponse<EntrySearchService.EntrySearchResult>> search(
			@RequestParam String q,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		if (page < 0) throw new IllegalArgumentException("The 'page' parameter must be at least 0.");
		if (size < 1 || size > 100) throw new IllegalArgumentException("The 'size' parameter must be between 1 and 100.");
		if (q.isBlank()) {
			throw new IllegalArgumentException("The 'q' parameter must not be blank.");
		}
		if (q.trim().length() > 200) {
			throw new IllegalArgumentException("The 'q' parameter must not exceed 200 characters.");
		}
		if (from != null && to != null && from.isAfter(to)) {
			throw new IllegalArgumentException("The 'from' date must not be after the 'to' date.");
		}
		return ApiResponse.success(entrySearchService.search(currentUser.id(), q.trim(), from, to, page, size));
	}
}
