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
	void userQuotasRemainIsolatedWhenOneUserExhaustsAllowance() {
		AtomicLong clock = new AtomicLong(0);
		// Allow 3 requests per 60 seconds
		RequestQuota quota = new RequestQuota(3, 10, Duration.ofSeconds(60), clock::get);

		// User A uses up their entire quota (3 requests allowed)
		assertThat(quota.retryAfter("user-a")).isZero();
		assertThat(quota.retryAfter("user-a")).isZero();
		assertThat(quota.retryAfter("user-a")).isZero();

		// User A's 4th request is throttled
		assertThat(quota.retryAfter("user-a")).isEqualTo(60);

		// User B is fresh and unaffected by User A's exhaustion
		assertThat(quota.retryAfter("user-b")).isZero();
		assertThat(quota.retryAfter("user-b")).isZero();
		assertThat(quota.retryAfter("user-b")).isZero();

		// User B's 4th request is now throttled independently
		assertThat(quota.retryAfter("user-b")).isEqualTo(60);

		// Advancing clock past the window boundary resets both users
		clock.set(Duration.ofSeconds(61).toNanos());
		assertThat(quota.retryAfter("user-a")).isZero();
		assertThat(quota.retryAfter("user-b")).isZero();
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
