package com.tlavu.moodly.shared.error.exception.common;

import com.tlavu.moodly.shared.error.code.global.GlobalErrorCode;
import com.tlavu.moodly.shared.error.exception.base.ApplicationException;

/** Uses the fixed NOT_FOUND code while callers supply a safe resource-specific message. */
public class ResourceNotFoundException extends ApplicationException {

	public ResourceNotFoundException(String message) {
		super(GlobalErrorCode.NOT_FOUND, message);
	}
}
