package com.tlavu.moodly.modules.auth.service.implementation;

import com.tlavu.moodly.modules.auth.model.UserProfile;
import com.tlavu.moodly.modules.auth.repository.UserProfileRepository;
import com.tlavu.moodly.modules.auth.security.CurrentUser;
import com.tlavu.moodly.modules.auth.security.CurrentUserIdentity;
import com.tlavu.moodly.modules.auth.service.view.ProfileView;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/** Explicitly synchronizes application-owned profile data from a verified Auth0 identity. */
@Service
@RequiredArgsConstructor
public class UserProfileService {
	private final CurrentUser currentUser;
	private final UserProfileRepository profiles;

	public ProfileView synchronizeCurrent() {
		CurrentUserIdentity identity = currentUser.identity();

		return ProfileView.from(
				profiles.findByAuth0Subject(identity.subject())
						.orElseGet(() -> create(identity))
		);
	}

	private UserProfile create(CurrentUserIdentity identity) {
		try {
			return profiles.save(
				new UserProfile(
					identity.subject(),
					identity.email(),
					Instant.now()
				)
			);
		} catch (DuplicateKeyException exception) {
			return profiles.findByAuth0Subject(identity.subject())
					.orElseThrow(() -> exception);
		}
	}
}
