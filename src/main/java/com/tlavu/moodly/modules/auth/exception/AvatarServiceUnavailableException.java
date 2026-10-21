package com.tlavu.moodly.modules.auth.exception;

import com.tlavu.moodly.shared.error.code.global.GlobalErrorCode;
import com.tlavu.moodly.shared.error.exception.base.ApplicationException;

/** Uses the fixed 503 avatar code and retains the underlying integration failure as its cause. */
public class AvatarServiceUnavailableException extends ApplicationException {

	public AvatarServiceUnavailableException(Throwable cause) {
		super(GlobalErrorCode.AVATAR_SERVICE_UNAVAILABLE, cause);
	}
}
