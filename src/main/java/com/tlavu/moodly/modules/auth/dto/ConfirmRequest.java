package com.tlavu.moodly.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record ConfirmRequest(
		@NotBlank
		String publicId,

		@Positive
		long version
) {}
