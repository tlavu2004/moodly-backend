package com.tlavu.moodly.modules.auth.service.view;

public record AvatarView(
		String publicId,
		String contentType,
		Long sizeBytes,
		String deliveryUrl
) {}
