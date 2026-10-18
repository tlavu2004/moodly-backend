package com.tlavu.moodly.modules.auth.integration.dto;

public record ConfirmedAsset(
		String publicId,
		long version,
		String contentType,
		long sizeBytes
) {}
