package com.tlavu.moodly.shared.presentation.dto.error;

import java.util.List;

public record ApiError(
		int status,
		String code,
		String message,
		String path,
		List<FieldErrorResponse> errors
) {}
