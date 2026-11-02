package com.tlavu.moodly.modules.e2e.exception;

import com.tlavu.moodly.modules.e2e.code.E2eErrorCode;
import com.tlavu.moodly.shared.error.exception.base.ApplicationException;

public final class E2eException extends ApplicationException {
	private E2eException(E2eErrorCode code) {
		super(code);
	}

	public static E2eException resetForbidden() {
		return new E2eException(E2eErrorCode.E2E_RESET_FORBIDDEN);
	}
}
