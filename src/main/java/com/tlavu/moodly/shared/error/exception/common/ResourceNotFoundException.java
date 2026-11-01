package com.tlavu.moodly.shared.error.exception.common;

import com.tlavu.moodly.shared.error.code.global.GlobalErrorCode;
import com.tlavu.moodly.shared.error.exception.base.ApplicationException;

/** Fixes the NOT_FOUND code; callers supply only a safe message naming the missing resource. */
public class ResourceNotFoundException extends ApplicationException {

	public ResourceNotFoundException(String message) {
		super(GlobalErrorCode.NOT_FOUND, message);
	}
}
