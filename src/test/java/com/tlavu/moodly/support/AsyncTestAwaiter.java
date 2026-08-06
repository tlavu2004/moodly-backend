package com.tlavu.moodly.support;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/** Bounded polling for eventually consistent integration assertions. */
public final class AsyncTestAwaiter {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);
	private static final Duration POLL_INTERVAL = Duration.ofMillis(100);

	private AsyncTestAwaiter() {
	}

	public static void until(String description, CheckedCondition condition, Supplier<String> diagnostic) throws Exception {
		var deadline = Instant.now().plus(DEFAULT_TIMEOUT);
		while (Instant.now().isBefore(deadline)) {
			if (condition.matches()) {
				return;
			}
			Thread.sleep(POLL_INTERVAL);
		}
		throw new AssertionError("Timed out waiting for " + description + ". Last observed state: " + diagnostic.get());
	}

	@FunctionalInterface
	public interface CheckedCondition {
		boolean matches() throws Exception;
	}
}
