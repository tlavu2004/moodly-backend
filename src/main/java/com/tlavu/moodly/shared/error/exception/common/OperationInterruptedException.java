package com.tlavu.moodly.shared.error.exception.common;

/** Cancellation of an application operation while waiting for another resource. */
public final class OperationInterruptedException extends RuntimeException {
	public OperationInterruptedException(String message, InterruptedException cause) {
		super(message, cause);
	}
}
