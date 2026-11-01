package com.tlavu.moodly.modules.auth.exception;

/** Invalid Auth0 HTTP client settings prevent the application from starting. */
public final class Auth0HttpConfigurationException extends IllegalArgumentException {
	public Auth0HttpConfigurationException() {
		super("Auth0 HTTP timeouts must be positive, attempts 1..5, delay non-negative.");
	}
}
