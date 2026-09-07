package com.tlavu.moodly.modules.auth.application;

import com.tlavu.moodly.modules.auth.domain.UserProfile;
import com.tlavu.moodly.modules.auth.infrastructure.UserProfileRepository;
import java.time.Instant;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/** Explicitly synchronizes application-owned profile data from a verified Auth0 identity. */
@Service
public class UserProfileService {

	private final CurrentUser currentUser;
	private final UserProfileRepository profiles;

	public UserProfileService(CurrentUser currentUser, UserProfileRepository profiles) {
		this.currentUser = currentUser;
		this.profiles = profiles;
	}

	public UserProfile synchronizeCurrent() {
		var identity = currentUser.identity();
		return profiles.findByAuth0Subject(identity.subject())
				.orElseGet(() -> create(identity));
	}

	private UserProfile create(CurrentUser.Identity identity) {
		try {
			return profiles.save(new UserProfile(identity.subject(), identity.email(), Instant.now()));
		} catch (DuplicateKeyException exception) {
			return profiles.findByAuth0Subject(identity.subject()).orElseThrow(() -> exception);
		}
	}
}
