package com.tlavu.moodly.modules.auth.security;

public record CurrentUserIdentity(
		String subject,
		String email
) {}
