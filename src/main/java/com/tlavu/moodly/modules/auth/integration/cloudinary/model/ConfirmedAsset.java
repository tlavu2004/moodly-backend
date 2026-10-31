package com.tlavu.moodly.modules.auth.integration.cloudinary.model;

public record ConfirmedAsset(
		String publicId,
		long version,
		String contentType,
		long sizeBytes
) {}
