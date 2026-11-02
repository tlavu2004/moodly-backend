package com.tlavu.moodly.shared.controller.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.tlavu.moodly.modules.auth.exception.AvatarException;
import com.tlavu.moodly.modules.auth.exception.AvatarServiceUnavailableException;
import com.tlavu.moodly.modules.auth.exception.AvatarProfileMissingException;
import com.tlavu.moodly.modules.search.exception.CdcReplayException;
import com.tlavu.moodly.modules.auth.code.AuthErrorCode;
import com.tlavu.moodly.modules.habits.code.HabitErrorCode;
import com.tlavu.moodly.shared.controller.dto.response.ApiResponse;
import com.tlavu.moodly.shared.error.code.global.GlobalErrorCode;
import com.tlavu.moodly.shared.error.exception.common.ConflictException;
import com.tlavu.moodly.shared.error.exception.common.InvalidRequestException;
import com.tlavu.moodly.shared.filter.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {
	@RestController
	static class DuplicateController {
		@GetMapping("/test-duplicate")
		void fail() {
			throw new DuplicateKeyException("internal index name and key");
		}
	}

	@Test
	void mvcDispatchKeepsUnknownDuplicateInternal() throws Exception {
		MockMvcBuilders.standaloneSetup(new DuplicateController())
				.setControllerAdvice(new GlobalExceptionHandler())
				.build()
				.perform(get("/test-duplicate"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error.code").value("INTERNAL_SERVER_ERROR"))
				.andExpect(jsonPath("$.error.message").value("An unexpected error occurred."));
	}

	@Test
	void keepsTheErrorCodeCatalogUnique() {
		List<String> codes = Stream.of(
				Arrays.stream(GlobalErrorCode.values()),
				Arrays.stream(AuthErrorCode.values()),
				Arrays.stream(HabitErrorCode.values())
		).flatMap(stream -> stream).map(code -> code.getCode()).toList();
		assertThat(codes).doesNotHaveDuplicates();
		assertThat(Arrays.stream(GlobalErrorCode.values()).map(GlobalErrorCode::getStatus)).doesNotContainNull();
		assertThat(Arrays.stream(AuthErrorCode.values()).map(AuthErrorCode::getStatus)).doesNotContainNull();
		assertThat(Arrays.stream(HabitErrorCode.values()).map(HabitErrorCode::getStatus)).doesNotContainNull();
	}

	@Test
	void derivesConflictStatusAndMessageFromItsCode() {
		HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
		when(request.getRequestURI()).thenReturn("/habits/habit-1");

		ResponseEntity<ApiResponse<Void>> response = new GlobalExceptionHandler().handleApplication(new ConflictException(), request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody().error().status()).isEqualTo(409);
		assertThat(response.getBody().error().code()).isEqualTo("CONFLICT");
		assertThat(response.getBody().error().message()).isEqualTo(GlobalErrorCode.CONFLICT.getDefaultMessage());
	}

	@Test
	void preservesAvatarErrorResponseDetails() {
		HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
		when(request.getRequestURI()).thenReturn("/me/avatar/confirm");
		AvatarException exception = AvatarException.uploadExpired();

		ResponseEntity<ApiResponse<Void>> response = new GlobalExceptionHandler().handleApplication(exception, request);

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
	void preservesAvatarServiceUnavailableResponseAndCause() {
		HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
		when(request.getRequestURI()).thenReturn("/me/avatar");
		IllegalStateException cause = new IllegalStateException("provider detail");
		AvatarServiceUnavailableException exception = new AvatarServiceUnavailableException(cause);

		ResponseEntity<ApiResponse<Void>> response = new GlobalExceptionHandler().handleApplication(exception, request);

		assertThat(exception.getCause()).isSameAs(cause);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(response.getBody().error().code()).isEqualTo("AVATAR_SERVICE_UNAVAILABLE");
		assertThat(response.getBody().error().message()).isEqualTo(AuthErrorCode.AVATAR_SERVICE_UNAVAILABLE.getDefaultMessage());
		assertThat(response.getBody().error().message()).doesNotContain("provider detail");
	}

	@Test
	void doesNotExposeUnexpectedExceptionDetails() {
		HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
		when(request.getRequestURI()).thenReturn("/example");

		ResponseEntity<ApiResponse<Void>> response = new GlobalExceptionHandler().handleUnexpectedException(
				new IllegalStateException("database-password=secret"), request);

		assertThat(response.getBody().error().code()).isEqualTo("INTERNAL_SERVER_ERROR");
		assertThat(response.getBody().error().message()).isEqualTo("An unexpected error occurred.");
		assertThat(response.getBody().error().message()).doesNotContain("secret");
		assertThat(response.getBody().error().requestId()).isEqualTo("unavailable");
	}

	@Test
	void distinguishesDeclaredRequestFailuresFromUnexpectedIllegalArguments() {
		HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
		when(request.getRequestURI()).thenReturn("/example");
		GlobalExceptionHandler handler = new GlobalExceptionHandler();

		ResponseEntity<ApiResponse<Void>> invalidRequest = handler.handleApplication(
				new InvalidRequestException("Page must be positive."), request);
		assertThat(invalidRequest.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(invalidRequest.getBody().error().code()).isEqualTo("INVALID_REQUEST");
		assertThat(invalidRequest.getBody().error().message()).isEqualTo("Page must be positive.");

		ResponseEntity<ApiResponse<Void>> unexpected = handler.handleUnexpectedException(
				new IllegalArgumentException("secret=internal"), request);
		assertThat(unexpected.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(unexpected.getBody().error().code()).isEqualTo("INTERNAL_SERVER_ERROR");
		assertThat(unexpected.getBody().error().message()).doesNotContain("secret");
	}

	@Test
	void leavesUnclassifiedDuplicateKeysAsInternalFailures() {
		HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
		when(request.getRequestURI()).thenReturn("/entries/today");

		ResponseEntity<ApiResponse<Void>> response = new GlobalExceptionHandler().handleUnexpectedException(
				new DuplicateKeyException("internal index name and key"), request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody().error().code()).isEqualTo("INTERNAL_SERVER_ERROR");
		assertThat(response.getBody().error().message()).doesNotContain("internal index name");
	}

	@Test
	void keepsInternalModuleFailuresAtFiveHundredWithoutLeakingDetails() {
		HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
		when(request.getRequestURI()).thenReturn("/internal/cdc/dead-letters/secret/replay");
		GlobalExceptionHandler handler = new GlobalExceptionHandler();

		for (Exception exception : List.of(
				new AvatarProfileMissingException(),
				new CdcReplayException("sensitive dead letter", new IllegalArgumentException("secret"))
		)) {
			ResponseEntity<ApiResponse<Void>> response = handler.handleUnexpectedException(exception, request);
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
			assertThat(response.getBody().error().code()).isEqualTo("INTERNAL_SERVER_ERROR");
			assertThat(response.getBody().error().message()).isEqualTo("An unexpected error occurred.");
		}
	}

	@Test
	void mapsControllerSecurityFailuresWithoutExposingTheirMessages() {
		HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
		when(request.getRequestURI()).thenReturn("/habits");
		GlobalExceptionHandler handler = new GlobalExceptionHandler();

		ResponseEntity<ApiResponse<Void>> denied = handler.handleAccessDenied(
				new org.springframework.security.access.AccessDeniedException("sensitive principal"), request);
		assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(denied.getBody().error().code()).isEqualTo("FORBIDDEN");
		assertThat(denied.getBody().error().message()).doesNotContain("sensitive principal");

		ResponseEntity<ApiResponse<Void>> unauthenticated = handler.handleAuthenticationFailure(
				new org.springframework.security.authentication.BadCredentialsException("sensitive token"), request);
		assertThat(unauthenticated.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(unauthenticated.getBody().error().code()).isEqualTo("UNAUTHORIZED");
		assertThat(unauthenticated.getBody().error().message()).doesNotContain("sensitive token");
	}

	@Test
	void logsSanitizedStackLocationsAndRequestIdWithoutExceptionMessages() {
		HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
		when(request.getRequestURI()).thenReturn("/example");
		Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
		ListAppender<ILoggingEvent> events = new ListAppender<>();
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
