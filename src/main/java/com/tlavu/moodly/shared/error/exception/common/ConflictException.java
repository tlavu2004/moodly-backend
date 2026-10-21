package com.tlavu.moodly.shared.error.exception.common;

import com.tlavu.moodly.shared.error.code.global.GlobalErrorCode;
import com.tlavu.moodly.shared.error.exception.base.ApplicationException;

/** Raised when a requested update uses a stale resource version. */
public class ConflictException extends ApplicationException {

	public ConflictException() {
		super(GlobalErrorCode.CONFLICT);
	}
}
