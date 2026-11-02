package com.tlavu.moodly.modules.auth.exception;

import com.tlavu.moodly.modules.auth.code.AuthErrorCode;
import com.tlavu.moodly.shared.error.exception.base.ApplicationException;
import com.tlavu.moodly.shared.error.ProviderHttpStatus;

/** Uses the fixed 503 avatar code and retains the underlying integration failure as its cause. */
public final class AvatarServiceUnavailableException extends ApplicationException implements ProviderHttpStatus {
	private final int providerStatus;

	public AvatarServiceUnavailableException(Throwable cause) {
		super(AuthErrorCode.AVATAR_SERVICE_UNAVAILABLE, cause);
		this.providerStatus = 0;
	}

	public AvatarServiceUnavailableException(Throwable cause, int providerStatus) {
		super(AuthErrorCode.AVATAR_SERVICE_UNAVAILABLE, cause);
		this.providerStatus = providerStatus;
	}

	@Override
	public int providerHttpStatus() {
		return providerStatus;
	}
}
