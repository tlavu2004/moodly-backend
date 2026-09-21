package com.tlavu.moodly.shared.presentation.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.tlavu.moodly.shared.application.exception.code.global.GlobalErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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
	}
}
