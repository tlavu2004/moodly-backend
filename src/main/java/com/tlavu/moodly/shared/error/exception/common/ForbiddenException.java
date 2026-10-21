package com.tlavu.moodly.shared.error.exception.common;

import com.tlavu.moodly.shared.error.code.global.GlobalErrorCode;
import com.tlavu.moodly.shared.error.exception.base.ApplicationException;

/** Rejects a protected operation with the fixed FORBIDDEN code and its default message. */
public class ForbiddenException extends ApplicationException {

	public ForbiddenException() {
		super(GlobalErrorCode.FORBIDDEN);
	}
}
