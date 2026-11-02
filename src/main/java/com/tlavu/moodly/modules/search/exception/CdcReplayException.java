package com.tlavu.moodly.modules.search.exception;

/** A CDC replay failed for a reason other than search infrastructure unavailability. */
public final class CdcReplayException extends IllegalStateException {
	public CdcReplayException(String message, Throwable cause) {
		super(message, cause);
	}
}
