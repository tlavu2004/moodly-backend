package com.tlavu.moodly.shared.error.exception;

/** Raised when Elasticsearch cannot serve a search or index maintenance operation. */
import com.tlavu.moodly.shared.error.code.global.GlobalErrorCode;
import org.springframework.http.HttpStatus;

public class SearchInfrastructureUnavailableException extends ApplicationException {

	public SearchInfrastructureUnavailableException(String message, Throwable cause) {
		super(GlobalErrorCode.SEARCH_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE, message, cause);
	}
}
