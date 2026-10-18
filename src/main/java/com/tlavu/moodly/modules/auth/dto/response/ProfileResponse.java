package com.tlavu.moodly.modules.auth.dto.response;

import com.tlavu.moodly.modules.auth.model.UserProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(requiredProperties = {"userId", "email", "createdAt", "updatedAt"})
public record ProfileResponse(
		String userId,
		String email,
		Instant createdAt,
		Instant updatedAt
) {

	public static ProfileResponse from(UserProfile profile) {
		return new ProfileResponse(
			profile.getAuth0Subject(),
			profile.getEmail(),
			profile.getCreatedAt(),
			profile.getUpdatedAt()
		);
	}
}
