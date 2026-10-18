package com.tlavu.moodly.modules.auth.dto;

import com.tlavu.moodly.modules.auth.service.AvatarService;

public record AvatarResponse(
		String publicId,
		String contentType,
		Long sizeBytes,
		String deliveryUrl
) {

	public static AvatarResponse from(AvatarService.Avatar avatar) {
		return new AvatarResponse(
			avatar.publicId(),
			avatar.contentType(),
			avatar.sizeBytes(),
			avatar.deliveryUrl()
		);
	}
}
