package com.tlavu.moodly.shared.error.exception;

/** Raised when a caller is not permitted to use a protected maintenance operation. */
import com.tlavu.moodly.shared.error.code.global.GlobalErrorCode;
import org.springframework.http.HttpStatus;

public class ForbiddenException extends ApplicationException {

	public ForbiddenException() {
		super(GlobalErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, null);
	}
}
