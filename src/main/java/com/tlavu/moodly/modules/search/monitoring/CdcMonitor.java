package com.tlavu.moodly.modules.search.monitoring;

import java.time.Instant;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
@Getter
public class CdcMonitor {
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
		lastFailureAt = Instant.now();
		lastFailure = exception.getMessage();
	}
}
