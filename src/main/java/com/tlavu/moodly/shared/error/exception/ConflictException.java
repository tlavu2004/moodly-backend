package com.tlavu.moodly.shared.error.exception;

import com.tlavu.moodly.shared.error.code.global.GlobalErrorCode;

/** Raised when a requested update uses a stale resource version. */
public class ConflictException extends ApplicationException {

	public ConflictException() {
		super(GlobalErrorCode.CONFLICT);
	}
}
