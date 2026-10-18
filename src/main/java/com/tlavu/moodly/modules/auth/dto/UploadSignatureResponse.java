package com.tlavu.moodly.modules.auth.dto;

import com.tlavu.moodly.modules.auth.service.result.UploadSignature;
import java.time.Instant;

public record UploadSignatureResponse(
		String cloudName,
		String apiKey,
		String uploadPreset,
		String publicId,
		long timestamp,
		Instant expiresAt,
		String signature,
		String uploadUrl
) {

	public static UploadSignatureResponse from(UploadSignature signature) {
		return new UploadSignatureResponse(
			signature.cloudName(),
			signature.apiKey(),
			signature.uploadPreset(),
			signature.publicId(),
			signature.timestamp(),
			signature.expiresAt(),
			signature.signature(),
			signature.uploadUrl()
		);
	}
}
