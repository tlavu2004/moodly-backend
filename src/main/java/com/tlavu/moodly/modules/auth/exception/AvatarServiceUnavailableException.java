package com.tlavu.moodly.modules.auth.exception;

import com.tlavu.moodly.shared.error.code.global.GlobalErrorCode;
import com.tlavu.moodly.shared.error.exception.ApplicationException;
import org.springframework.http.HttpStatus;

public class AvatarServiceUnavailableException extends ApplicationException {

	public AvatarServiceUnavailableException(Throwable cause) {
		super(GlobalErrorCode.AVATAR_SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE, null, cause);
	}
}
