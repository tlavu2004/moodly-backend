package com.tlavu.moodly.modules.auth.security.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.tlavu.moodly.modules.auth.exception.Auth0HttpConfigurationException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

class Auth0HttpClientTest {
	private final HttpRequest request = mock(HttpRequest.class);
	private final byte[] body = new byte[0];
	private final ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);

	@Test
	void retriesTimeoutThenReturnsSuccessfulResponse() throws Exception {
		ClientHttpResponse response = mock(ClientHttpResponse.class);
		when(execution.execute(request, body)).thenThrow(new SocketTimeoutException()).thenReturn(response);
		assertThat(Auth0HttpClient.retry(3, Duration.ZERO).intercept(request, body, execution)).isSameAs(response);
		verify(execution, times(2)).execute(request, body);
	}

	@Test
	void stopsAfterConfiguredAttempts() throws Exception {
		when(execution.execute(request, body)).thenThrow(new SocketTimeoutException());
		assertThatThrownBy(() -> Auth0HttpClient.retry(3, Duration.ZERO).intercept(request, body, execution))
				.isInstanceOf(SocketTimeoutException.class);
		verify(execution, times(3)).execute(request, body);
	}

	@Test
	void doesNotRetryCertificateFailure() throws Exception {
		when(execution.execute(request, body)).thenThrow(new SSLHandshakeException("certificate failure"));
		assertThatThrownBy(() -> Auth0HttpClient.retry(3, Duration.ZERO).intercept(request, body, execution))
				.isInstanceOf(SSLHandshakeException.class);
		verify(execution).execute(request, body);
	}

	@Test
	void doesNotRetryHttpErrorResponse() throws Exception {
		ClientHttpResponse response = mock(ClientHttpResponse.class);
		when(response.getStatusCode()).thenReturn(org.springframework.http.HttpStatus.UNAUTHORIZED);
		when(execution.execute(request, body)).thenReturn(response);
		assertThat(Auth0HttpClient.retry(3, Duration.ZERO).intercept(request, body, execution)).isSameAs(response);
		verify(execution).execute(request, body);
	}

	@Test
	void preservesInterruptionWithoutRetry() throws Exception {
		when(execution.execute(request, body)).thenThrow(new SocketTimeoutException());
		Thread.currentThread().interrupt();
		try {
			assertThatThrownBy(() -> Auth0HttpClient.retry(3, Duration.ZERO).intercept(request, body, execution))
					.isInstanceOf(SocketTimeoutException.class);
			assertThat(Thread.currentThread().isInterrupted()).isTrue();
			verify(execution).execute(request, body);
		} finally {
			Thread.interrupted();
		}
	}

	@Test
	void rejectsUnboundedOrInvalidConfiguration() {
		assertThatThrownBy(() -> Auth0HttpClient.create(Duration.ZERO, Duration.ofSeconds(10), 3, Duration.ZERO))
				.isInstanceOf(Auth0HttpConfigurationException.class);
		assertThatThrownBy(() -> Auth0HttpClient.create(Duration.ofSeconds(10), Duration.ofSeconds(10), 6, Duration.ZERO))
				.isInstanceOf(Auth0HttpConfigurationException.class);
	}
}
