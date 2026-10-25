package com.tlavu.moodly.modules.auth.integration.cloudinary.contract;

public record ConfirmedAsset(
		String publicId,
		long version,
		String contentType,
		long sizeBytes
) {}
