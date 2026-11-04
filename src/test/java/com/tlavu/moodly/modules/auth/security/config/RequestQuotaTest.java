package com.tlavu.moodly.modules.auth.security.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RequestQuotaTest {
	@Test
	void isolatesSubjectsAndRestoresQuotaAtTheWindowBoundary() {
		AtomicLong clock = new AtomicLong();
		RequestQuota quota = new RequestQuota(2, 2, Duration.ofSeconds(60), clock::get);

		assertThat(quota.retryAfter("alice")).isZero();
		assertThat(quota.retryAfter("alice")).isZero();
		assertThat(quota.retryAfter("alice")).isEqualTo(60);
		assertThat(quota.retryAfter("bob")).isZero();
		assertThat(quota.retryAfter("charlie")).isEqualTo(60);
		assertThat(quota.retryAfter("bob")).isZero();

		clock.set(Duration.ofSeconds(60).toNanos() - 1);
		assertThat(quota.retryAfter("alice")).isEqualTo(1);
		clock.incrementAndGet();
		assertThat(quota.retryAfter("charlie")).isZero();
		assertThat(quota.retryAfter("alice")).isZero();
	}

	@Test
	void concurrentRequestsCannotOverspendTheSameQuota() throws Exception {
		RequestQuota quota = new RequestQuota(5, 10, Duration.ofMinutes(1), () -> 0);
		CountDownLatch start = new CountDownLatch(1);
		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			Callable<Long> request = () -> {
				start.await();
				return quota.retryAfter("alice");
			};
			List<Future<Long>> futures = IntStream.range(0, 32).mapToObj(index -> executor.submit(request)).toList();
			start.countDown();
			int admitted = 0;
			for (Future<Long> future : futures) {
				if (future.get() == 0) {
					admitted++;
				}
			}
			assertThat(admitted).isEqualTo(5);
		}
	}
}
