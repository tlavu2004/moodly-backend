package com.tlavu.moodly.shared.presentation.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tlavu.moodly.shared.presentation.dto.error.ApiError;

import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean success,
		@Schema(nullable = true, description = "Present for successful responses.") T data,
		@Schema(nullable = true, description = "Present for error responses.") ApiError error,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant timestamp
) {

	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>(
				true,
				data,
				null,
				Instant.now()
		);
	}

	public static <T> ApiResponse<T> error(ApiError error) {
		return new ApiResponse<>(
				false,
				null,
				error,
				Instant.now()
		);
	}
}
