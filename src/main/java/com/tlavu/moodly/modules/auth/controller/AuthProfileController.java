package com.tlavu.moodly.modules.auth.controller;

import com.tlavu.moodly.modules.auth.dto.response.ProfileResponse;
import com.tlavu.moodly.modules.auth.service.implementation.UserProfileService;
import com.tlavu.moodly.shared.controller.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/profile")
@Tag(name = "Authentication", description = "Authenticated user profile operations")
@RequiredArgsConstructor
public class AuthProfileController {

	private final UserProfileService profiles;

	/** Idempotent post-authentication bootstrap; credentials and tokens remain owned by Auth0. */
	@PutMapping
	@Operation(
			summary = "Synchronize the current user profile",
			description = "Creates or refreshes the profile for the authenticated Auth0 user. This operation is idempotent."
	)
	public ApiResponse<ProfileResponse> synchronize() {
		return ApiResponse.success(ProfileResponse.from(profiles.synchronizeCurrent()));
	}
}
