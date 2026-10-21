package com.tlavu.moodly.shared.error.exception.common;

import com.tlavu.moodly.shared.error.code.global.GlobalErrorCode;
import com.tlavu.moodly.shared.error.exception.base.ApplicationException;

/** The protected constructor is for subclasses with specific request-error codes, such as AvatarException. */
public class InvalidRequestException extends ApplicationException {

	public InvalidRequestException(String message) {
		super(GlobalErrorCode.INVALID_REQUEST, message);
	}

	protected InvalidRequestException(GlobalErrorCode code, String safeMessage) {
		super(code, safeMessage);
	}
}
