package com.tlavu.moodly.shared.error.logging;

import com.tlavu.moodly.modules.search.infrastructure.elasticsearch.ElasticsearchException;
import com.mongodb.MongoException;
import com.tlavu.moodly.modules.auth.exception.AvatarAssetDeletionException;
import java.io.IOException;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ExceptionLogSummaryTest {
	@Test
	void summarizesCausesAndApplicationLocationsWithoutMessages() {
		RuntimeException failure = new IllegalStateException("password=secret",
				new IllegalArgumentException("token=secret"));

		assertThat(ExceptionLogSummary.of(failure))
				.contains("IllegalStateException at ", "ExceptionLogSummaryTest", "<- IllegalArgumentException")
				.doesNotContain("password=", "token=", "secret", "\n");
	}

	@Test
	void handlesExceptionsWithoutAStackTrace() {
		RuntimeException failure = Mockito.mock(RuntimeException.class);
		Mockito.when(failure.getStackTrace()).thenReturn(null);
		assertThat(ExceptionLogSummary.of(failure)).isEqualTo("RuntimeException");
	}

	@Test
	void includesOnlyTypedProviderStatusAndMongoCode() {
		ElasticsearchException elasticsearch = Mockito.mock(ElasticsearchException.class);
		Mockito.when(elasticsearch.status()).thenReturn(429);
		assertThat(ExceptionLogSummary.of(elasticsearch)).contains("ElasticsearchException[httpStatus=429]");
		assertThat(ExceptionLogSummary.of(new AvatarAssetDeletionException("secret", 503)))
				.contains("AvatarAssetDeletionException[httpStatus=503]")
				.doesNotContain("secret");
		assertThat(ExceptionLogSummary.of(new MongoException(11000, "secret")))
				.contains("MongoException[mongoCode=11000]")
				.doesNotContain("secret");
	}

	@Test
	void includesExternalOriginWithoutItsMessage() {
		IOException failure = new IOException("private-provider-response");
		failure.setStackTrace(new StackTraceElement[] {
				new StackTraceElement("java.net.http.HttpClient", "send", "HttpClient.java", 42),
				new StackTraceElement(getClass().getName(), "includesExternalOriginWithoutItsMessage", "ExceptionLogSummaryTest.java", 29)
		});
		assertThat(ExceptionLogSummary.of(failure))
				.contains("IOException at ", " from java.net.http.HttpClient.send:42")
				.doesNotContain("private-provider-response", "\n");
	}
}
