package com.tlavu.moodly.shared.error.exception.common;

import com.tlavu.moodly.shared.error.code.global.GlobalErrorCode;
import com.tlavu.moodly.shared.error.exception.base.ApplicationException;

/** Fixes the INVALID_REQUEST code; callers supply only a safe message. */
public class InvalidRequestException extends ApplicationException {

	public InvalidRequestException(String message) {
		super(GlobalErrorCode.INVALID_REQUEST, message);
	}
}
