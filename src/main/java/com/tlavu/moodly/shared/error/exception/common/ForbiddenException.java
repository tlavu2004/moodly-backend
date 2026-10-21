package com.tlavu.moodly.shared.error.exception.common;

import com.tlavu.moodly.shared.error.code.global.GlobalErrorCode;
import com.tlavu.moodly.shared.error.exception.base.ApplicationException;

/** Raised when a caller is not permitted to use a protected maintenance operation. */
public class ForbiddenException extends ApplicationException {

	public ForbiddenException() {
		super(GlobalErrorCode.FORBIDDEN);
	}
}
