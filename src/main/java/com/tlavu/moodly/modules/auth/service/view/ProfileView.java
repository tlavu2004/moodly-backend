package com.tlavu.moodly.modules.auth.service.view;

import com.tlavu.moodly.modules.auth.model.UserProfile;
import java.time.Instant;

public record ProfileView(String userId, String email, Instant createdAt, Instant updatedAt) {

	public static ProfileView from(UserProfile profile) {
		return new ProfileView(
				profile.getAuth0Subject(),
				profile.getEmail(),
				profile.getCreatedAt(),
				profile.getUpdatedAt()
		);
	}
}
