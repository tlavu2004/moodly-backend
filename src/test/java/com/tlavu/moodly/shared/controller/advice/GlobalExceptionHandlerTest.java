package com.tlavu.moodly.shared.controller.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.tlavu.moodly.modules.auth.exception.AvatarException;
import com.tlavu.moodly.shared.error.code.global.GlobalErrorCode;
import com.tlavu.moodly.shared.error.exception.ConflictException;
import com.tlavu.moodly.shared.error.exception.InvalidRequestException;
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
		assertThat(Arrays.stream(GlobalErrorCode.values()).map(GlobalErrorCode::getStatus)).doesNotContainNull();
	}

	@Test
	void derivesConflictStatusAndMessageFromItsCode() {
		var request = Mockito.mock(HttpServletRequest.class);
		when(request.getRequestURI()).thenReturn("/habits/habit-1");

		var response = new GlobalExceptionHandler().handleApplication(new ConflictException(), request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody().error().status()).isEqualTo(409);
		assertThat(response.getBody().error().code()).isEqualTo("CONFLICT");
		assertThat(response.getBody().error().message()).isEqualTo(GlobalErrorCode.CONFLICT.getDefaultMessage());
	}

	@Test
	void preservesAvatarErrorResponseDetails() {
		var request = Mockito.mock(HttpServletRequest.class);
		when(request.getRequestURI()).thenReturn("/me/avatar/confirm");
		var exception = AvatarException.uploadExpired();

		var response = new GlobalExceptionHandler().handleApplication(exception, request);

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
	void distinguishesDeclaredRequestFailuresFromUnexpectedIllegalArguments() {
		var request = Mockito.mock(HttpServletRequest.class);
		when(request.getRequestURI()).thenReturn("/example");
		var handler = new GlobalExceptionHandler();

		var invalidRequest = handler.handleApplication(new InvalidRequestException("Page must be positive."), request);
		assertThat(invalidRequest.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(invalidRequest.getBody().error().code()).isEqualTo("INVALID_REQUEST");
		assertThat(invalidRequest.getBody().error().message()).isEqualTo("Page must be positive.");

		var unexpected = handler.handleUnexpectedException(new IllegalArgumentException("secret=internal"), request);
		assertThat(unexpected.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(unexpected.getBody().error().code()).isEqualTo("INTERNAL_SERVER_ERROR");
		assertThat(unexpected.getBody().error().message()).doesNotContain("secret");
	}

	@Test
	void mapsControllerSecurityFailuresWithoutExposingTheirMessages() {
		var request = Mockito.mock(HttpServletRequest.class);
		when(request.getRequestURI()).thenReturn("/habits");
		var handler = new GlobalExceptionHandler();

		var denied = handler.handleAccessDenied(
				new org.springframework.security.access.AccessDeniedException("sensitive principal"), request);
		assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(denied.getBody().error().code()).isEqualTo("FORBIDDEN");
		assertThat(denied.getBody().error().message()).doesNotContain("sensitive principal");

		var unauthenticated = handler.handleAuthenticationFailure(
				new org.springframework.security.authentication.BadCredentialsException("sensitive token"), request);
		assertThat(unauthenticated.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(unauthenticated.getBody().error().code()).isEqualTo("UNAUTHORIZED");
		assertThat(unauthenticated.getBody().error().message()).doesNotContain("sensitive token");
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
