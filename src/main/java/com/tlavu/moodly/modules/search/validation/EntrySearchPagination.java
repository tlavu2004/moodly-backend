package com.tlavu.moodly.modules.search.validation;

import com.tlavu.moodly.shared.error.exception.common.InvalidRequestException;

public final class EntrySearchPagination {

	private static final int MAX_RESULT_WINDOW = 10_000;

	private EntrySearchPagination() {
	}

	public static int validatedOffset(int page, int size) {
		if (page < 0) {
			throw new InvalidRequestException("The 'page' parameter must be at least 0.");
		}
		if (size < 1 || size > 100) {
			throw new InvalidRequestException("The 'size' parameter must be between 1 and 100.");
		}

		long offset = (long) page * size;

		if (offset + size > MAX_RESULT_WINDOW) {
			throw new InvalidRequestException(
				"The requested page exceeds the searchable result window of " +
				MAX_RESULT_WINDOW +
				"."
			);
		}

		return Math.toIntExact(offset);
	}
}
