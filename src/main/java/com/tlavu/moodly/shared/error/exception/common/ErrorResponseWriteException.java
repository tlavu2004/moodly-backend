package com.tlavu.moodly.shared.error.exception.common;

import java.io.IOException;

/** The HTTP error envelope could not be written to the response stream. */
public final class ErrorResponseWriteException extends RuntimeException {
	public ErrorResponseWriteException(IOException cause) {
		super("Could not write the HTTP error response", cause);
	}
}
