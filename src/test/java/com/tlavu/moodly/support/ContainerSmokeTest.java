package com.tlavu.moodly.support;

import static com.tlavu.moodly.support.AutomationTooling.JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ContainerSmokeTest {
	@Test
	void stoppedFixtureFailsBeforeProbing() {
		assertThatThrownBy(() -> ContainerSmoke.await("MongoDB", "fixture", 180, () -> fail("Unexpected probe"),
				() -> JSON.readTree("{\"Running\":false,\"ExitCode\":1}"), () -> 0, () -> fail("Unexpected sleep")))
				.hasMessageContaining("exit=1");
	}

	@Test
	void retriesUntilTheProbeSucceeds() throws Exception {
		var probes = new AtomicInteger();
		var pauses = new AtomicInteger();
		ContainerSmoke.await("MongoDB", "fixture", 180, () -> probes.incrementAndGet() == 2,
				() -> JSON.readTree("{\"Running\":true}"), () -> 0, pauses::incrementAndGet);
		assertThat(probes.get()).isEqualTo(2);
		assertThat(pauses.get()).isEqualTo(1);
	}

	@Test
	void stopsPollingAtTheDeadline() {
		var clock = new AtomicLong();
		assertThatThrownBy(() -> ContainerSmoke.await("MongoDB", "fixture", 180, () -> false,
				() -> JSON.readTree("{\"Running\":true}"), clock::get, () -> clock.set(Duration.ofSeconds(180).toNanos())))
				.hasMessageContaining("Timed out waiting for MongoDB");
	}
}
