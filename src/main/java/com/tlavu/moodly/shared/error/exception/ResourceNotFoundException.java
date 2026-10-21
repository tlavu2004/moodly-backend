package com.tlavu.moodly.shared.error.exception;

import com.tlavu.moodly.shared.error.code.global.GlobalErrorCode;

public class ResourceNotFoundException extends ApplicationException {

	public ResourceNotFoundException(String message) {
		super(GlobalErrorCode.NOT_FOUND, message);
	}
}
