package com.tlavu.moodly.modules.auth.presentation;

import com.tlavu.moodly.modules.auth.application.UserProfileService;
import com.tlavu.moodly.modules.auth.domain.UserProfile;
import com.tlavu.moodly.shared.presentation.dto.response.ApiResponse;
import java.time.Instant;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/profile")
public class AuthProfileController {

	private final UserProfileService profiles;

	public AuthProfileController(UserProfileService profiles) {
		this.profiles = profiles;
	}

	/** Idempotent post-authentication bootstrap; credentials and tokens remain owned by Auth0. */
	@PutMapping
	public ApiResponse<ProfileResponse> synchronize() {
		return ApiResponse.success(ProfileResponse.from(profiles.synchronizeCurrent()));
	}

	public record ProfileResponse(String userId, String email, Instant createdAt, Instant updatedAt) {
		private static ProfileResponse from(UserProfile profile) {
			return new ProfileResponse(profile.getAuth0Subject(), profile.getEmail(), profile.getCreatedAt(), profile.getUpdatedAt());
		}
	}
}
