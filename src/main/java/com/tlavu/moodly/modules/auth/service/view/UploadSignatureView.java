package com.tlavu.moodly.modules.auth.service.view;

import java.time.Instant;

public record UploadSignatureView(
		String cloudName,
		String apiKey,
		String uploadPreset,
		String publicId,
		long timestamp,
		Instant expiresAt,
		String signature,
		String uploadUrl
) {}
