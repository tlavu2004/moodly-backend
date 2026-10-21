package com.tlavu.moodly.modules.auth.dto.result;

public record CurrentUserIdentity(
		String subject,
		String email
) {}
