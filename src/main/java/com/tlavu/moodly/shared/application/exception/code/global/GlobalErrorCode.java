package com.tlavu.moodly.shared.application.exception.code.global;

import com.tlavu.moodly.shared.application.exception.code.contract.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GlobalErrorCode implements ErrorCode {

	VALIDATION_FAILED("VALIDATION_FAILED", "Request validation failed."),
	INVALID_REQUEST("INVALID_REQUEST", "The request is invalid."),
	MISSING_REQUIRED_HEADER("MISSING_REQUIRED_HEADER", "A required request header is missing."),
	DUPLICATE_RESOURCE("DUPLICATE_RESOURCE", "A document with the same unique key already exists."),
	FORBIDDEN("FORBIDDEN", "You are not allowed to perform this operation."),
	SEARCH_UNAVAILABLE("SEARCH_UNAVAILABLE", "Search is temporarily unavailable."),
	INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "An unexpected error occurred.");

	private final String code;
	private final String defaultMessage;
}
