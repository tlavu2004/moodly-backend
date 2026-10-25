package com.tlavu.moodly.modules.auth.dto.response;

import com.tlavu.moodly.modules.auth.service.view.ProfileView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(requiredProperties = {"userId", "email", "createdAt", "updatedAt"})
public record ProfileResponse(
		String userId,
		String email,
		Instant createdAt,
		Instant updatedAt
) {
	public static ProfileResponse from(ProfileView profile) {
		return new ProfileResponse(
			profile.userId(),
			profile.email(),
			profile.createdAt(),
			profile.updatedAt()
		);
	}
}
