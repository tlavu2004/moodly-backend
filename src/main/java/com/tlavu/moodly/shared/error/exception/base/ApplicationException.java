package com.tlavu.moodly.shared.error.exception.base;

import com.tlavu.moodly.shared.error.code.contract.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Base for expected failures; subclasses select the public code, which also determines HTTP status. */
public abstract class ApplicationException extends RuntimeException {

	@Getter
	private final ErrorCode code;

	protected ApplicationException(ErrorCode code) {
		super(code.getDefaultMessage());
		this.code = code;
	}

	protected ApplicationException(
			ErrorCode code,
			String safeMessage
	) {
		super(safeMessage);
		this.code = code;
	}

	protected ApplicationException(
			ErrorCode code,
			Throwable cause
	) {
		super(code.getDefaultMessage(), cause);
		this.code = code;
	}

	protected ApplicationException(
			ErrorCode code,
			String safeMessage,
			Throwable cause
	) {
		super(safeMessage, cause);
		this.code = code;
	}

	public HttpStatus getStatus() {
		return code.getStatus();
	}
}
