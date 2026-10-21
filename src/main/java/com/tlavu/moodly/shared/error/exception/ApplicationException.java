package com.tlavu.moodly.shared.error.exception;

import com.tlavu.moodly.shared.error.code.contract.ErrorCode;
import org.springframework.http.HttpStatus;

/** An expected application failure with a stable public code and HTTP mapping. */
public abstract class ApplicationException extends RuntimeException {

	private final ErrorCode code;
	protected ApplicationException(ErrorCode code) {
		super(code.getDefaultMessage());
		this.code = code;
	}

	protected ApplicationException(ErrorCode code, String safeMessage) {
		super(safeMessage);
		this.code = code;
	}

	protected ApplicationException(ErrorCode code, Throwable cause) {
		super(code.getDefaultMessage(), cause);
		this.code = code;
	}

	protected ApplicationException(ErrorCode code, String safeMessage, Throwable cause) {
		super(safeMessage, cause);
		this.code = code;
	}

	public ErrorCode getCode() { return code; }

	public HttpStatus getStatus() { return code.getStatus(); }
}
