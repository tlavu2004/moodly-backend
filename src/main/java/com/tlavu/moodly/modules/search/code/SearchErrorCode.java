package com.tlavu.moodly.modules.search.code;

import com.tlavu.moodly.shared.error.code.contract.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SearchErrorCode implements ErrorCode {
	SEARCH_INVALID_REQUEST("SEARCH_INVALID_REQUEST", "The search request is invalid.", HttpStatus.BAD_REQUEST),
	SEARCH_UNAVAILABLE("SEARCH_UNAVAILABLE", "Search is temporarily unavailable.", HttpStatus.SERVICE_UNAVAILABLE),
	CDC_MAINTENANCE_FORBIDDEN("CDC_MAINTENANCE_FORBIDDEN", "You are not allowed to perform this operation.", HttpStatus.FORBIDDEN),
	CDC_DEAD_LETTER_NOT_FOUND("CDC_DEAD_LETTER_NOT_FOUND", "CDC dead letter was not found.", HttpStatus.NOT_FOUND);

	private final String code;
	private final String defaultMessage;
	private final HttpStatus status;
}
