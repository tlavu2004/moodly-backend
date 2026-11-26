package com.tlavu.moodly.modules.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record ConfirmRequest(
		@NotBlank String publicId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Positive long version
) {}
