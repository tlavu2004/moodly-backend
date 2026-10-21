package com.tlavu.moodly.shared.error.exception;

import com.tlavu.moodly.shared.error.code.contract.ErrorCode;
import com.tlavu.moodly.shared.error.code.global.GlobalErrorCode;
import org.springframework.http.HttpStatus;

public class InvalidRequestException extends ApplicationException {

	public InvalidRequestException(String message) {
		this(GlobalErrorCode.INVALID_REQUEST, message);
	}

	public InvalidRequestException(ErrorCode code, String message) {
		super(code, HttpStatus.BAD_REQUEST, message);
	}
}
