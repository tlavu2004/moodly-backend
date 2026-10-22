package com.tlavu.moodly.modules.auth.exception;

import com.tlavu.moodly.modules.auth.code.AuthErrorCode;
import com.tlavu.moodly.shared.error.exception.base.ApplicationException;

/** Each factory pairs an avatar-specific 400 code with its safe message for the caller. */
public class AvatarException extends ApplicationException {

	private AvatarException(AuthErrorCode code, String safeMessage) {
		super(code, safeMessage);
	}

	public static AvatarException unsupportedContentType() {
		return new AvatarException(
				AuthErrorCode.AVATAR_CONTENT_TYPE_UNSUPPORTED,
				"Avatar content type must be image/jpeg, image/png, or image/webp."
		);
	}

	public static AvatarException invalidFileSize() {
		return new AvatarException(
				AuthErrorCode.AVATAR_FILE_TOO_LARGE,
				"Avatar must be between 1 byte and 5 MiB."
		);
	}

	public static AvatarException uploadNotFound() {
		return new AvatarException(
				AuthErrorCode.AVATAR_UPLOAD_NOT_FOUND,
				"Avatar upload was not found."
		);
	}

	public static AvatarException uploadExpired() {
		return new AvatarException(
				AuthErrorCode.AVATAR_UPLOAD_NOT_FOUND,
				"Avatar upload is unknown or has expired."
		);
	}

	public static AvatarException versionMismatch() {
		return new AvatarException(
				AuthErrorCode.AVATAR_ASSET_INVALID,
				"Avatar version does not match the uploaded asset."
		);
	}

	public static AvatarException invalidMetadata() {
		return new AvatarException(
				AuthErrorCode.AVATAR_ASSET_INVALID,
				"Uploaded avatar metadata is invalid."
		);
	}
}
