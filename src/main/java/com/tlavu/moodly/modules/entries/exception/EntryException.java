package com.tlavu.moodly.modules.entries.exception;

import com.tlavu.moodly.modules.entries.code.EntryErrorCode;
import com.tlavu.moodly.shared.error.exception.base.ApplicationException;

public final class EntryException extends ApplicationException {
	private EntryException(EntryErrorCode code, String safeMessage) {
		super(code, safeMessage);
	}

	public static EntryException invalidRange(String safeMessage) {
		return new EntryException(EntryErrorCode.ENTRY_INVALID_RANGE, safeMessage);
	}
}
