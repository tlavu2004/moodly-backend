package com.tlavu.moodly.shared.error.exception;

import com.tlavu.moodly.shared.error.code.global.GlobalErrorCode;

public class InvalidRequestException extends ApplicationException {

	public InvalidRequestException(String message) {
		super(GlobalErrorCode.INVALID_REQUEST, message);
	}

	protected InvalidRequestException(GlobalErrorCode code, String safeMessage) {
		super(code, safeMessage);
	}
}
