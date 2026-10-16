package com.tlavu.moodly.modules.auth.service;

import com.tlavu.moodly.shared.application.exception.code.global.GlobalErrorCode;

public class AvatarException extends RuntimeException {
	private final GlobalErrorCode code;

	public AvatarException(GlobalErrorCode code, String message) {
		super(message);
		this.code = code;
	}

	public GlobalErrorCode getCode() { return code; }
}
