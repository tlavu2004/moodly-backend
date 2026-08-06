package com.tlavu.moodly.shared.application.exception;

/** Raised when Elasticsearch cannot serve a search or index maintenance operation. */
public class SearchInfrastructureUnavailableException extends RuntimeException {

	public SearchInfrastructureUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
