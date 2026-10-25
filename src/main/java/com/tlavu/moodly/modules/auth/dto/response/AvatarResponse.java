package com.tlavu.moodly.modules.auth.dto.response;

import com.tlavu.moodly.modules.auth.service.view.AvatarView;

public record AvatarResponse(
		String publicId,
		String contentType,
		Long sizeBytes,
		String deliveryUrl
) {
	public static AvatarResponse from(AvatarView avatar) {
		return new AvatarResponse(
			avatar.publicId(),
			avatar.contentType(),
			avatar.sizeBytes(),
			avatar.deliveryUrl()
		);
	}
}
