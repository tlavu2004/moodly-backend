package com.tlavu.moodly.modules.auth.exception;

import com.tlavu.moodly.shared.error.code.global.GlobalErrorCode;

public class AvatarException extends com.tlavu.moodly.shared.error.exception.InvalidRequestException {

	public AvatarException(GlobalErrorCode code, String message) {
		super(code, message);
	}

}
