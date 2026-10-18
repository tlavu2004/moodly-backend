package com.tlavu.moodly.modules.auth.dto.result;

public record Avatar(
		String publicId,
		String contentType,
		Long sizeBytes,
		String deliveryUrl
) {}
