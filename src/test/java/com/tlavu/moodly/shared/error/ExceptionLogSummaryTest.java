package com.tlavu.moodly.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ExceptionLogSummaryTest {
	@Test
	void summarizesCausesAndApplicationLocationsWithoutMessages() {
		RuntimeException failure = new IllegalStateException("password=secret",
				new IllegalArgumentException("token=secret"));

		assertThat(ExceptionLogSummary.of(failure))
				.contains("IllegalStateException at ", "ExceptionLogSummaryTest", "<- IllegalArgumentException")
				.doesNotContain("password=", "token=", "secret", "\n");
	}

	@Test
	void handlesExceptionsWithoutAStackTrace() {
		RuntimeException failure = Mockito.mock(RuntimeException.class);
		Mockito.when(failure.getStackTrace()).thenReturn(null);
		assertThat(ExceptionLogSummary.of(failure)).isEqualTo("RuntimeException");
	}
}
