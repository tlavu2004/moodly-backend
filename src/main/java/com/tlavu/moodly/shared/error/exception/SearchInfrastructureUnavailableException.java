package com.tlavu.moodly.shared.error.exception;

import com.tlavu.moodly.shared.error.code.global.GlobalErrorCode;

/** Raised when Elasticsearch cannot serve a search or index maintenance operation. */
public class SearchInfrastructureUnavailableException extends ApplicationException {

	private SearchInfrastructureUnavailableException(String safeMessage, Throwable cause) {
		super(GlobalErrorCode.SEARCH_UNAVAILABLE, safeMessage, cause);
	}

	public static SearchInfrastructureUnavailableException duringSearch(Throwable cause) {
		return new SearchInfrastructureUnavailableException("Elasticsearch search is unavailable", cause);
	}

	public static SearchInfrastructureUnavailableException duringIndexInitialization(Throwable cause) {
		return new SearchInfrastructureUnavailableException("Elasticsearch index initialization is unavailable", cause);
	}

	public static SearchInfrastructureUnavailableException duringReindex(Throwable cause) {
		return new SearchInfrastructureUnavailableException("Elasticsearch reindex is unavailable", cause);
	}

	public static SearchInfrastructureUnavailableException duringReplay(Throwable cause) {
		return new SearchInfrastructureUnavailableException("CDC dead-letter replay is unavailable", cause);
	}

	public static SearchInfrastructureUnavailableException duringE2eIndexing(Throwable cause) {
		return new SearchInfrastructureUnavailableException("E2E reset search indexing is unavailable", cause);
	}

	public static SearchInfrastructureUnavailableException duringE2eVerification(Throwable cause) {
		return new SearchInfrastructureUnavailableException("E2E reset search indexing could not be verified", cause);
	}
}
