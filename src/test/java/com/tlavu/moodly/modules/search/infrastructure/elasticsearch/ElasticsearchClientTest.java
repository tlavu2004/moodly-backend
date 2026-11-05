package com.tlavu.moodly.modules.search.infrastructure.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class ElasticsearchClientTest {
	@Test
	void wrapsIoFailureAndPreservesItsCause() throws Exception {
		HttpClient http = mock(HttpClient.class);
		IOException failure = new IOException("connection unavailable");
		doThrow(failure).when(http).send(any(HttpRequest.class), any());

		try (ElasticsearchClient client = client(http)) {
			assertThatThrownBy(() -> client.exists("entries"))
					.isInstanceOf(ElasticsearchTransportException.class)
					.hasCause(failure);
		}
	}

	@Test
	void wrapsInterruptionAndRestoresTheInterruptFlag() throws Exception {
		HttpClient http = mock(HttpClient.class);
		InterruptedException failure = new InterruptedException("interrupted");
		doThrow(failure).when(http).send(any(HttpRequest.class), any());

		try (ElasticsearchClient client = client(http)) {
			assertThatThrownBy(() -> client.exists("entries"))
					.isInstanceOf(ElasticsearchTransportException.class)
					.hasCause(failure);
			assertThat(Thread.currentThread().isInterrupted()).isTrue();
		} finally {
			Thread.interrupted();
		}
	}

	@Test
	void retainsHttpStatusFailuresForCallerClassification() throws Exception {
		HttpClient http = mock(HttpClient.class);
		@SuppressWarnings("unchecked")
		HttpResponse<String> response = mock(HttpResponse.class);
		org.mockito.Mockito.when(response.statusCode()).thenReturn(429);
		doReturn(response).when(http).send(any(HttpRequest.class), any());

		try (ElasticsearchClient client = client(http)) {
			assertThatThrownBy(() -> client.exists("entries"))
					.isInstanceOfSatisfying(ElasticsearchException.class,
							failure -> assertThat(failure.status()).isEqualTo(429));
		}
	}

	private ElasticsearchClient client(HttpClient http) {
		ElasticsearchClient client = new ElasticsearchClient(new ObjectMapper(), "http://localhost:9200", "", "");
		client.close();
		ReflectionTestUtils.setField(client, "http", http);
		return client;
	}
}
