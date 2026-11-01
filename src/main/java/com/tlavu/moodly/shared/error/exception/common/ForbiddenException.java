package com.tlavu.moodly.shared.error.exception.common;

import com.tlavu.moodly.shared.error.code.global.GlobalErrorCode;
import com.tlavu.moodly.shared.error.exception.base.ApplicationException;

/** Fixes the FORBIDDEN code and default message; callers need no context argument or code choice. */
public class ForbiddenException extends ApplicationException {

	public ForbiddenException() {
		super(GlobalErrorCode.FORBIDDEN);
	}
}
