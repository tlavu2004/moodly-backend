package com.tlavu.moodly.modules.auth.service.input;

public record ConfirmAvatarInput(
		String publicId,
		long version
) {}
