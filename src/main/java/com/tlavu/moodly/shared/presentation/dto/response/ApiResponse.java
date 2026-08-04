package com.tlavu.moodly.shared.presentation.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tlavu.moodly.shared.presentation.dto.error.ApiError;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
		boolean success,
		T data,
		ApiError error,
		Instant timestamp
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
