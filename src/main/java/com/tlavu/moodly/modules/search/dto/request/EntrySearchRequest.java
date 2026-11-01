package com.tlavu.moodly.modules.search.dto.request;

import com.tlavu.moodly.modules.search.validation.EntrySearchPagination;
import com.tlavu.moodly.shared.error.exception.common.InvalidRequestException;
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
			throw new InvalidRequestException("The 'q' parameter must not be blank.");
		}
		if (q.trim().length() > 200) {
			throw new InvalidRequestException("The 'q' parameter must not exceed 200 characters.");
		}
		if (
				from != null &&
				to != null &&
				from.isAfter(to)
		) {
			throw new InvalidRequestException("The 'from' date must not be after the 'to' date.");
		}

		q = q.trim();
	}
}
