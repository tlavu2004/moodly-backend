package com.tlavu.moodly.modules.auth.security;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/** Bounded retries for transient discovery/JWKS network failures, never token validation. */
final class Auth0HttpClient {
	private Auth0HttpClient() {}

	static RestTemplate create(Duration connectTimeout, Duration readTimeout, int attempts, Duration delay) {
		if (connectTimeout.isNegative() || connectTimeout.isZero()
				|| readTimeout.isNegative() || readTimeout.isZero()
				|| attempts < 1 || attempts > 5 || delay.isNegative()) {
			throw new IllegalArgumentException("Auth0 HTTP timeouts must be positive, attempts 1..5, delay non-negative");
		}
		var factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(connectTimeout);
		factory.setReadTimeout(readTimeout);
		var client = new RestTemplate(factory);
		client.getInterceptors().add(retry(attempts, delay));
		return client;
	}

	static ClientHttpRequestInterceptor retry(int attempts, Duration delay) {
		return (request, body, execution) -> {
			for (int attempt = 1; ; attempt++) {
				try {
					return execution.execute(request, body);
				} catch (IOException failure) {
					// Do not replay HTTP errors, TLS failures, or arbitrary I/O errors.
					if (Thread.currentThread().isInterrupted() || attempt >= attempts
							|| !(failure instanceof SocketTimeoutException || failure instanceof ConnectException)) {
						throw failure;
					}
					LoggerFactory.getLogger(Auth0HttpClient.class).warn(
							"Auth0 network request failed ({}); retry {}/{}", failure.getClass().getSimpleName(), attempt + 1, attempts);
					try {
						Thread.sleep(delay);
					} catch (InterruptedException interrupted) {
						Thread.currentThread().interrupt();
						var cancelled = new InterruptedIOException("Auth0 HTTP retry interrupted");
						cancelled.initCause(interrupted);
						throw cancelled;
					}
				}
			}
		};
	}
}
