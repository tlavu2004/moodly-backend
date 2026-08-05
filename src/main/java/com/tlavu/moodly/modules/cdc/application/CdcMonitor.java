package com.tlavu.moodly.modules.cdc.application;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
@Getter
public class CdcMonitor {

	private final AtomicLong failedEventCount = new AtomicLong();
	private volatile boolean listenerHealthy;
	private volatile Instant lastFailureAt;
	private volatile String lastFailure;

	public void listenerStarted() {
		listenerHealthy = true;
	}

	public void listenerFailed(Throwable exception) {
		listenerHealthy = false;
		lastFailureAt = Instant.now();
		lastFailure = exception.getMessage();
	}

	public void deadLettered(Throwable exception) {
		failedEventCount.incrementAndGet();
		lastFailureAt = Instant.now();
		lastFailure = exception.getMessage();
	}

	public void replayedSuccessfully() {
		failedEventCount.updateAndGet(count -> Math.max(0, count - 1));
	}

}
