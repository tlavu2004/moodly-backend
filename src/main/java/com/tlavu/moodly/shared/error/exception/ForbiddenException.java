package com.tlavu.moodly.shared.error.exception;

import com.tlavu.moodly.shared.error.code.global.GlobalErrorCode;

/** Raised when a caller is not permitted to use a protected maintenance operation. */
public class ForbiddenException extends ApplicationException {

	public ForbiddenException() {
		super(GlobalErrorCode.FORBIDDEN);
	}
}
