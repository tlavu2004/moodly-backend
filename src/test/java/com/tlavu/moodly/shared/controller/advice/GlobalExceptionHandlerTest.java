package com.tlavu.moodly.shared.controller.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.tlavu.moodly.modules.auth.exception.AvatarException;
import com.tlavu.moodly.shared.error.code.global.GlobalErrorCode;
import com.tlavu.moodly.shared.filter.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

class GlobalExceptionHandlerTest {

	@Test
	void keepsTheErrorCodeCatalogUnique() {
		var codes = Arrays.stream(GlobalErrorCode.values()).map(GlobalErrorCode::getCode).toList();
		assertThat(codes).doesNotHaveDuplicates();
	}

	@Test
	void preservesAvatarErrorResponseDetails() {
		var request = Mockito.mock(HttpServletRequest.class);
		when(request.getRequestURI()).thenReturn("/me/avatar/confirm");
		var exception = new AvatarException(
				GlobalErrorCode.AVATAR_UPLOAD_NOT_FOUND,
				"Avatar upload is unknown or has expired."
		);

		var response = new GlobalExceptionHandler().handleAvatar(exception, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().success()).isFalse();
		assertThat(response.getBody().error().status()).isEqualTo(400);
		assertThat(response.getBody().error().code()).isEqualTo("AVATAR_UPLOAD_NOT_FOUND");
		assertThat(response.getBody().error().message()).isEqualTo("Avatar upload is unknown or has expired.");
		assertThat(response.getBody().error().path()).isEqualTo("/me/avatar/confirm");
		assertThat(response.getBody().error().errors()).isEmpty();
		assertThat(response.getBody().error().requestId()).isEqualTo("unavailable");
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
