package com.tlavu.moodly.modules.auth.application;

public class AvatarException extends RuntimeException {
	private final Code code;

	public AvatarException(Code code, String message) {
		super(message);
		this.code = code;
	}

	public Code getCode() { return code; }

	public enum Code {
		AVATAR_CONTENT_TYPE_UNSUPPORTED,
		AVATAR_FILE_TOO_LARGE,
		AVATAR_UPLOAD_NOT_FOUND
	}
}
