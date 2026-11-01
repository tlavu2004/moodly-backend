package com.tlavu.moodly.modules.search.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tlavu.moodly.modules.search.repository.CdcDeadLetterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;

@ExtendWith(MockitoExtension.class)
class CdcHealthIndicatorTest {
	@Mock
	private CdcDeadLetterRepository deadLetterRepository;

	@Test
	void readsCachedCountWithoutQueryingMongoOnEveryHealthCheck() {
		CdcMonitor monitor = new CdcMonitor();
		monitor.listenerStarted();
		when(deadLetterRepository.count()).thenReturn(2L)
				.thenThrow(new IllegalStateException("MongoDB unavailable"))
				.thenReturn(1L);
		CdcHealthIndicator indicator = new CdcHealthIndicator(monitor, deadLetterRepository);

		indicator.initializeDeadLetterCount();
		assertThat(indicator.health().getStatus()).isEqualTo(Health.up().build().getStatus());
		assertThat(indicator.health().getDetails()).containsEntry("failedEventCount", 2L);
		verify(deadLetterRepository).count();

		indicator.refreshDeadLetterCount();
		assertThat(indicator.health().getStatus()).isEqualTo(Health.down().build().getStatus());
		assertThat(indicator.health().getDetails()).containsEntry("failedEventCount", 2L);
		verify(deadLetterRepository, times(2)).count();

		indicator.refreshDeadLetterCount();
		assertThat(indicator.health().getStatus()).isEqualTo(Health.up().build().getStatus());
		assertThat(indicator.health().getDetails()).containsEntry("failedEventCount", 1L);
		verify(deadLetterRepository, times(3)).count();
	}
}
