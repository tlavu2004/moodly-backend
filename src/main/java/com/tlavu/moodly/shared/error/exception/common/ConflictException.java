package com.tlavu.moodly.shared.error.exception.common;

import com.tlavu.moodly.shared.error.code.global.GlobalErrorCode;
import com.tlavu.moodly.shared.error.exception.base.ApplicationException;

/** Uses the fixed CONFLICT code and default message; a code-taking constructor is unnecessary without subclasses. */
public class ConflictException extends ApplicationException {

	public ConflictException() {
		super(GlobalErrorCode.CONFLICT);
	}
}
