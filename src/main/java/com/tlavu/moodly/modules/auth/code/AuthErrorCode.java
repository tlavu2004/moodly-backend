package com.tlavu.moodly.modules.auth.code;

import com.tlavu.moodly.shared.error.code.contract.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {
	AVATAR_CONTENT_TYPE_UNSUPPORTED("AVATAR_CONTENT_TYPE_UNSUPPORTED", "The avatar content type is unsupported.", HttpStatus.BAD_REQUEST),
	AVATAR_FILE_TOO_LARGE("AVATAR_FILE_TOO_LARGE", "The avatar file exceeds the allowed size.", HttpStatus.BAD_REQUEST),
	AVATAR_UPLOAD_NOT_FOUND("AVATAR_UPLOAD_NOT_FOUND", "The avatar upload was not found or has expired.", HttpStatus.BAD_REQUEST),
	AVATAR_ASSET_INVALID("AVATAR_ASSET_INVALID", "The uploaded avatar could not be verified.", HttpStatus.BAD_REQUEST),
	AVATAR_SERVICE_UNAVAILABLE("AVATAR_SERVICE_UNAVAILABLE", "Avatar service is temporarily unavailable.", HttpStatus.SERVICE_UNAVAILABLE);

	private final String code;
	private final String defaultMessage;
	private final HttpStatus status;
}
