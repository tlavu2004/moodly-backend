package com.tlavu.moodly.modules.auth.integration;

public record ConfirmedAsset(
		String publicId,
		long version,
		String contentType,
		long sizeBytes
) {}
