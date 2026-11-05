package com.tlavu.moodly.modules.auth.security.config;

import com.tlavu.moodly.modules.auth.exception.RateLimitConfigurationException;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

final class RequestQuota {
	private final int requests;
	private final int maxSubjects;
	private final long windowNanos;
	private final LongSupplier nanoTime;
	private final Map<String, Integer> counts = new HashMap<>();
	private long windowStart;

	RequestQuota(
			int requests,
			int maxSubjects,
			Duration window,
			LongSupplier nanoTime
	) {
		if (
				requests < 1 ||
				maxSubjects < 1 ||
				window.isZero() ||
				window.isNegative()
		) {
			throw new RateLimitConfigurationException("Rate limit requests, max subjects and window must be positive");
		}

		this.requests = requests;
		this.maxSubjects = maxSubjects;
		this.windowNanos = window.toNanos();
		this.nanoTime = nanoTime;
		this.windowStart = nanoTime.getAsLong();
	}

	// Zero admits the request. Positive values are the remaining wait in seconds.
	synchronized long retryAfter(String subject) {
		long now = nanoTime.getAsLong();
		long elapsed = now - windowStart;

		if (elapsed >= windowNanos) {
			counts.clear();
			windowStart = now;
			elapsed = 0;
		}

		Integer count = counts.get(subject);

		if (count == null && counts.size() < maxSubjects) {
			counts.put(subject, 1);
			return 0;
		}
		if (count != null && count < requests) {
			counts.put(subject, count + 1);
			return 0;
		}

		return Math.ceilDiv(windowNanos - elapsed, 1_000_000_000L);
	}
}
