package com.tlavu.moodly.modules.auth.dto.response;

import com.tlavu.moodly.modules.auth.dto.result.Avatar;

public record AvatarResponse(
		String publicId,
		String contentType,
		Long sizeBytes,
		String deliveryUrl
) {

	public static AvatarResponse from(Avatar avatar) {
		return new AvatarResponse(
			avatar.publicId(),
			avatar.contentType(),
			avatar.sizeBytes(),
			avatar.deliveryUrl()
		);
	}
}
