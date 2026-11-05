package com.tlavu.moodly.modules.auth.exception;

public final class RateLimitConfigurationException extends IllegalArgumentException {
	public RateLimitConfigurationException(String message) {
		super(message);
	}

}
