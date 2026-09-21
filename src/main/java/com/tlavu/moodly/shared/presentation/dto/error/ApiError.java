package com.tlavu.moodly.shared.presentation.dto.error;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

public record ApiError(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int status,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String path,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<FieldErrorResponse> errors
) {}
