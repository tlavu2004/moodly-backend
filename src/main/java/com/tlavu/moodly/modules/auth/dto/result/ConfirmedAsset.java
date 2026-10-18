package com.tlavu.moodly.modules.auth.dto.result;

public record ConfirmedAsset(
		String publicId,
		long version,
		String contentType,
		long sizeBytes
) {}
