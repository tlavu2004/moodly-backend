package com.tlavu.moodly.modules.auth.identity;

public record CurrentUserIdentity(
		String subject,
		String email
) {}
