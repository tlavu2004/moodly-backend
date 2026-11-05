package com.tlavu.moodly.modules.search.validation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import com.tlavu.moodly.modules.search.exception.SearchException;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EntrySearchPagination {

	private static final int MAX_RESULT_WINDOW = 10_000;


	public static int validatedOffset(int page, int size) {
		if (page < 0) {
			throw SearchException.invalidRequest(
					"The 'page' parameter must be at least 0."
			);
		}
		if (size < 1 || size > 100) {
			throw SearchException.invalidRequest(
					"The 'size' parameter must be between 1 and 100."
			);
		}

		long offset = (long) page * size;

		if (offset + size > MAX_RESULT_WINDOW) {
			throw SearchException.invalidRequest(
				"The requested page exceeds the searchable result window of " +
				MAX_RESULT_WINDOW + "."
			);
		}

		return Math.toIntExact(offset);
	}
}
