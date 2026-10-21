package com.tlavu.moodly.shared.error.exception;

import com.tlavu.moodly.shared.error.code.contract.ErrorCode;
import org.springframework.http.HttpStatus;

/** An expected application failure with a stable public code and HTTP mapping. */
public abstract class ApplicationException extends RuntimeException {

	private final ErrorCode code;
	private final HttpStatus status;

	protected ApplicationException(ErrorCode code, HttpStatus status, String message) {
		this(code, status, message, null);
	}

	protected ApplicationException(ErrorCode code, HttpStatus status, String message, Throwable cause) {
		super(message == null || message.isBlank() ? code.getDefaultMessage() : message, cause);
		this.code = code;
		this.status = status;
	}

	public ErrorCode getCode() { return code; }

	public HttpStatus getStatus() { return status; }
}
