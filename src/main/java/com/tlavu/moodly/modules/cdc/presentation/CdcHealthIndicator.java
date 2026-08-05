package com.tlavu.moodly.modules.cdc.presentation;

import com.tlavu.moodly.modules.cdc.application.CdcMonitor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** Exposed at /actuator/health as the "cdc" contributor. */
@Component("cdc")
public class CdcHealthIndicator implements HealthIndicator {

	private final CdcMonitor monitor;

	public CdcHealthIndicator(CdcMonitor monitor) {
		this.monitor = monitor;
	}

	@Override
	public Health health() {
		var builder = monitor.isListenerHealthy() ? Health.up() : Health.down();
		builder
				.withDetail("listenerHealthy", monitor.isListenerHealthy())
				.withDetail("failedEventCount", monitor.getFailedEventCount());
		if (monitor.getLastFailureAt() != null) {
			builder.withDetail("lastFailureAt", monitor.getLastFailureAt());
		}
		if (monitor.getLastFailure() != null) {
			builder.withDetail("lastFailure", monitor.getLastFailure());
		}
		return builder.build();
	}
}
