package com.tlavu.moodly.modules.auth.integration.result;

public record ConfirmedAsset(
		String publicId,
		long version,
		String contentType,
		long sizeBytes
) {}
