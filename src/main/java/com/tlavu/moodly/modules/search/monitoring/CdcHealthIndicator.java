package com.tlavu.moodly.modules.search.monitoring;

import com.tlavu.moodly.shared.error.logging.ExceptionLogSummary;

import com.tlavu.moodly.modules.search.repository.CdcDeadLetterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Exposed at /actuator/health as the "cdc" contributor. */
@Component("cdc")
@RequiredArgsConstructor
@Slf4j
public class CdcHealthIndicator implements HealthIndicator {
	private final CdcMonitor monitor;
	private final CdcDeadLetterRepository deadLetterRepository;
	private volatile long failedEventCount;
	private volatile boolean deadLetterCountAvailable;

	@EventListener(ApplicationReadyEvent.class)
	public void initializeDeadLetterCount() {
		refreshDeadLetterCount();
	}

	@Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
	public void refreshDeadLetterCount() {
		try {
			failedEventCount = deadLetterRepository.count();
			deadLetterCountAvailable = true;
		} catch (RuntimeException exception) {
			deadLetterCountAvailable = false;
			log.warn("Could not refresh CDC dead-letter count: {}", ExceptionLogSummary.of(exception));
		}
	}

	@Override
	public Health health() {
		boolean listenerHealthy = monitor.isListenerHealthy();
		Health.Builder builder = !listenerHealthy || !deadLetterCountAvailable
				? Health.down()
				: failedEventCount > 0 ? Health.status(new Status("DEGRADED")) : Health.up();
		builder.withDetail("listenerHealthy", listenerHealthy)
				.withDetail("failedEventCount", failedEventCount);

		if (monitor.getLastFailureAt() != null) {
			builder.withDetail(
					"lastFailureAt",
					monitor.getLastFailureAt()
			);
		}
		if (monitor.getLastFailure() != null) {
			builder.withDetail(
					"lastFailure",
					monitor.getLastFailure()
			);
		}

		return builder.build();
	}
}
