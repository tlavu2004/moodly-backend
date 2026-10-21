package com.tlavu.moodly.shared.error.exception.common;

import com.tlavu.moodly.shared.error.code.global.GlobalErrorCode;
import com.tlavu.moodly.shared.error.exception.base.ApplicationException;

/**
 * The public constructor fixes INVALID_REQUEST and accepts a safe message.
 * The protected constructor lets subclasses such as AvatarException use a specific request-error code.
 */
public class InvalidRequestException extends ApplicationException {

	public InvalidRequestException(String message) {
		super(GlobalErrorCode.INVALID_REQUEST, message);
	}

	protected InvalidRequestException(
		GlobalErrorCode code,
		String safeMessage
	) {
		super(code, safeMessage);
	}
}
