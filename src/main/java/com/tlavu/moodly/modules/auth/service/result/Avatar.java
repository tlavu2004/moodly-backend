package com.tlavu.moodly.modules.auth.service.result;

public record Avatar(
		String publicId,
		String contentType,
		Long sizeBytes,
		String deliveryUrl
) {}
