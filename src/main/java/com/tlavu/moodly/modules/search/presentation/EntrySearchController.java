package com.tlavu.moodly.modules.search.presentation;

import com.tlavu.moodly.modules.search.application.EntrySearchService;
import com.tlavu.moodly.modules.auth.application.CurrentUser;
import com.tlavu.moodly.shared.presentation.dto.response.ApiResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/entries/search")
public class EntrySearchController {

	private final EntrySearchService entrySearchService;
	private final CurrentUser currentUser;

	public EntrySearchController(EntrySearchService entrySearchService, CurrentUser currentUser) {
		this.entrySearchService = entrySearchService;
		this.currentUser = currentUser;
	}

	@GetMapping
	public ApiResponse<List<EntrySearchService.EntrySearchResult>> search(
			@RequestParam String q,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
	) {
		if (q.isBlank()) {
			throw new IllegalArgumentException("The 'q' parameter must not be blank.");
		}
		if (from != null && to != null && from.isAfter(to)) {
			throw new IllegalArgumentException("The 'from' date must not be after the 'to' date.");
		}
		return ApiResponse.success(entrySearchService.search(currentUser.id(), q.trim(), from, to));
	}
}
