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
	CONFLICT("CONFLICT", "The resource was changed by another request."),
	UNAUTHORIZED("UNAUTHORIZED", "Authentication is required or the access token is invalid."),
	FORBIDDEN("FORBIDDEN", "You are not allowed to perform this operation."),
	NOT_FOUND("NOT_FOUND", "The requested resource was not found."),
	SEARCH_UNAVAILABLE("SEARCH_UNAVAILABLE", "Search is temporarily unavailable."),
	AVATAR_CONTENT_TYPE_UNSUPPORTED("AVATAR_CONTENT_TYPE_UNSUPPORTED", "The avatar content type is unsupported."),
	AVATAR_FILE_TOO_LARGE("AVATAR_FILE_TOO_LARGE", "The avatar file exceeds the allowed size."),
	AVATAR_UPLOAD_NOT_FOUND("AVATAR_UPLOAD_NOT_FOUND", "The avatar upload was not found or has expired."),
	INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "An unexpected error occurred.");

	private final String code;
	private final String defaultMessage;
}
