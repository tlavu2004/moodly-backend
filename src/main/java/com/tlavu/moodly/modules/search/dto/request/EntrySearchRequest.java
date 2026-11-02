package com.tlavu.moodly.modules.search.dto.request;

import com.tlavu.moodly.modules.search.validation.EntrySearchPagination;
import com.tlavu.moodly.modules.search.exception.SearchException;
import java.time.LocalDate;

public record EntrySearchRequest(
		String q,
		LocalDate from,
		LocalDate to,
		int page,
		int size
) {
	public EntrySearchRequest {
		EntrySearchPagination.validatedOffset(page, size);

		if (q.isBlank()) {
			throw SearchException.invalidRequest("The 'q' parameter must not be blank.");
		}
		if (q.trim().length() > 200) {
			throw SearchException.invalidRequest("The 'q' parameter must not exceed 200 characters.");
		}
		if (
				from != null &&
				to != null &&
				from.isAfter(to)
		) {
			throw SearchException.invalidRequest("The 'from' date must not be after the 'to' date.");
		}

		q = q.trim();
	}
}
