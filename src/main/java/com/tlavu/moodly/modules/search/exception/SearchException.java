package com.tlavu.moodly.modules.search.exception;

import com.tlavu.moodly.modules.search.code.SearchErrorCode;
import com.tlavu.moodly.shared.error.exception.base.ApplicationException;

public final class SearchException extends ApplicationException {
	private SearchException(SearchErrorCode code) {
		super(code);
	}

	private SearchException(SearchErrorCode code, String safeMessage) {
		super(code, safeMessage);
	}

	public static SearchException invalidRequest(String safeMessage) {
		return new SearchException(SearchErrorCode.SEARCH_INVALID_REQUEST, safeMessage);
	}

	public static SearchException maintenanceForbidden() {
		return new SearchException(SearchErrorCode.CDC_MAINTENANCE_FORBIDDEN);
	}

	public static SearchException deadLetterNotFound() {
		return new SearchException(SearchErrorCode.CDC_DEAD_LETTER_NOT_FOUND);
	}
}
