package com.tlavu.moodly.shared.error.exception;

import com.tlavu.moodly.shared.error.code.contract.ErrorCode;
import com.tlavu.moodly.shared.error.code.global.GlobalErrorCode;
import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApplicationException {

	public ResourceNotFoundException(String message) {
		this(GlobalErrorCode.NOT_FOUND, message);
	}

	public ResourceNotFoundException(ErrorCode code, String message) {
		super(code, HttpStatus.NOT_FOUND, message);
	}
}
