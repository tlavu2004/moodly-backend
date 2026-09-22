package com.tlavu.moodly.shared.presentation.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.tlavu.moodly.shared.application.exception.code.global.GlobalErrorCode;
import com.tlavu.moodly.shared.infrastructure.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class GlobalExceptionHandlerTest {

	@Test
	void keepsTheErrorCodeCatalogUnique() {
		var codes = Arrays.stream(GlobalErrorCode.values()).map(GlobalErrorCode::getCode).toList();
		assertThat(codes).doesNotHaveDuplicates();
	}

	@Test
	void doesNotExposeUnexpectedExceptionDetails() {
		var request = Mockito.mock(HttpServletRequest.class);
		when(request.getRequestURI()).thenReturn("/example");

		var response = new GlobalExceptionHandler().handleUnexpectedException(
				new IllegalStateException("database-password=secret"), request);

		assertThat(response.getBody().error().code()).isEqualTo("INTERNAL_SERVER_ERROR");
		assertThat(response.getBody().error().message()).isEqualTo("An unexpected error occurred.");
		assertThat(response.getBody().error().message()).doesNotContain("secret");
		assertThat(response.getBody().error().requestId()).isEqualTo("unavailable");
	}

	@Test
	void logsSanitizedStackLocationsAndRequestIdWithoutExceptionMessages() {
		var request = Mockito.mock(HttpServletRequest.class);
		when(request.getRequestURI()).thenReturn("/example");
		var logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
		var events = new ListAppender<ILoggingEvent>();
		events.start();
		logger.addAppender(events);
		MDC.put(RequestIdFilter.MDC_KEY, "review-test-request");

		try {
			new GlobalExceptionHandler().handleUnexpectedException(
					new IllegalStateException("database-password=secret", new RuntimeException("access-token=secret")), request);
		} finally {
			MDC.remove(RequestIdFilter.MDC_KEY);
			logger.detachAppender(events);
			events.stop();
		}

		assertThat(events.list).singleElement().satisfies(event -> {
			assertThat(event.getMDCPropertyMap()).containsEntry(RequestIdFilter.MDC_KEY, "review-test-request");
			assertThat(event.getFormattedMessage())
					.contains("Unexpected request failure at /example", "java.lang.IllegalStateException",
							"Caused by: java.lang.RuntimeException", "GlobalExceptionHandlerTest.java")
					.doesNotContain("database-password", "access-token", "secret");
			assertThat(event.getThrowableProxy()).isNull();
		});
	}
}
