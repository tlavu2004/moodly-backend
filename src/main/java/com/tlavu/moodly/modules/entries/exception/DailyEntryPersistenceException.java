package com.tlavu.moodly.modules.entries.exception;

public final class DailyEntryPersistenceException extends IllegalStateException {
	public DailyEntryPersistenceException(String message) {
		super(message);
	}

	public DailyEntryPersistenceException(String message, Throwable cause) {
		super(message, cause);
	}
}
