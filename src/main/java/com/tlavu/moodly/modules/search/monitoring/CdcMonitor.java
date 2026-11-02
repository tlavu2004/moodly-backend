package com.tlavu.moodly.modules.search.monitoring;

import com.tlavu.moodly.shared.error.ExceptionLogSummary;
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
		lastFailure = ExceptionLogSummary.of(exception);
	}

	public void deadLettered(Throwable exception) {
		lastFailureAt = Instant.now();
		lastFailure = ExceptionLogSummary.of(exception);
	}
}
