package com.tlavu.moodly.modules.auth.dto.result;

import java.time.Instant;

public record UploadSignature(
		String cloudName,
		String apiKey,
		String uploadPreset,
		String publicId,
		long timestamp,
		Instant expiresAt,
		String signature,
		String uploadUrl
) {}
