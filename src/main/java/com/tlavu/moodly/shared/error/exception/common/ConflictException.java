package com.tlavu.moodly.shared.error.exception.common;

import com.tlavu.moodly.shared.error.code.global.GlobalErrorCode;
import com.tlavu.moodly.shared.error.exception.base.ApplicationException;

/**
 * Fixes the CONFLICT code and default message, so callers need no arguments.
 * A protected code constructor is only needed if a subclass uses a different code.
 */
public class ConflictException extends ApplicationException {

	public ConflictException() {
		super(GlobalErrorCode.CONFLICT);
	}
}
