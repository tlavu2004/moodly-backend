package com.tlavu.moodly.shared.application.exception;

/** Raised when a caller is not permitted to use a protected maintenance operation. */
public class ForbiddenException extends RuntimeException {

	public ForbiddenException() {
		super("You are not allowed to perform this operation.");
	}
}
