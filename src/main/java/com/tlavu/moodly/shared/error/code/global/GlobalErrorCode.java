package com.tlavu.moodly.shared.error.code.global;

import com.tlavu.moodly.shared.error.code.contract.ErrorCode;
import org.springframework.http.HttpStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GlobalErrorCode implements ErrorCode {

	VALIDATION_FAILED("VALIDATION_FAILED", "Request validation failed.", HttpStatus.BAD_REQUEST),
	INVALID_REQUEST("INVALID_REQUEST", "The request is invalid.", HttpStatus.BAD_REQUEST),
	METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", "The HTTP method is not supported for this endpoint.", HttpStatus.METHOD_NOT_ALLOWED),
	UNSUPPORTED_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE", "The request content type is not supported.", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
	NOT_ACCEPTABLE("NOT_ACCEPTABLE", "The requested response content type is not supported.", HttpStatus.NOT_ACCEPTABLE),
	MISSING_REQUIRED_HEADER("MISSING_REQUIRED_HEADER", "A required request header is missing.", HttpStatus.BAD_REQUEST),
	DUPLICATE_RESOURCE("DUPLICATE_RESOURCE", "A document with the same unique key already exists.", HttpStatus.CONFLICT),
	CONFLICT("CONFLICT", "The resource was changed by another request.", HttpStatus.CONFLICT),
	UNAUTHORIZED("UNAUTHORIZED", "Authentication is required or the access token is invalid.", HttpStatus.UNAUTHORIZED),
	FORBIDDEN("FORBIDDEN", "You are not allowed to perform this operation.", HttpStatus.FORBIDDEN),
	NOT_FOUND("NOT_FOUND", "The requested resource was not found.", HttpStatus.NOT_FOUND),
	SEARCH_UNAVAILABLE("SEARCH_UNAVAILABLE", "Search is temporarily unavailable.", HttpStatus.SERVICE_UNAVAILABLE),
	AVATAR_CONTENT_TYPE_UNSUPPORTED("AVATAR_CONTENT_TYPE_UNSUPPORTED", "The avatar content type is unsupported.", HttpStatus.BAD_REQUEST),
	AVATAR_FILE_TOO_LARGE("AVATAR_FILE_TOO_LARGE", "The avatar file exceeds the allowed size.", HttpStatus.BAD_REQUEST),
	AVATAR_UPLOAD_NOT_FOUND("AVATAR_UPLOAD_NOT_FOUND", "The avatar upload was not found or has expired.", HttpStatus.BAD_REQUEST),
	AVATAR_ASSET_INVALID("AVATAR_ASSET_INVALID", "The uploaded avatar could not be verified.", HttpStatus.BAD_REQUEST),
	AVATAR_SERVICE_UNAVAILABLE("AVATAR_SERVICE_UNAVAILABLE", "Avatar service is temporarily unavailable.", HttpStatus.SERVICE_UNAVAILABLE),
	INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "An unexpected error occurred.", HttpStatus.INTERNAL_SERVER_ERROR);

	private final String code;
	private final String defaultMessage;
	private final HttpStatus status;
}
