package com.tlavu.moodly.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record UploadRequest(
		@Schema(allowableValues = {"image/jpeg", "image/png", "image/webp"})
		@NotBlank
		String contentType,

		@Schema(description = "File size in bytes; maximum 5 MiB (5242880 bytes).", maximum = "5242880")
		@Positive
		long sizeBytes
) {}
