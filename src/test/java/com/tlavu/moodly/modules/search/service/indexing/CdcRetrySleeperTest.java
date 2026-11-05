package com.tlavu.moodly.modules.search.service.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tlavu.moodly.shared.error.exception.common.OperationInterruptedException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class CdcRetrySleeperTest {
	@Test
	void wrapsInterruptionAndPreservesCancellation() {
		Thread.currentThread().interrupt();
		try {
			assertThatThrownBy(() -> new CdcRetrySleeper().sleep(Duration.ofMillis(1)))
					.isInstanceOf(OperationInterruptedException.class)
					.hasCauseInstanceOf(InterruptedException.class);
			assertThat(Thread.currentThread().isInterrupted()).isTrue();
		} finally {
			Thread.interrupted();
		}
	}
}
