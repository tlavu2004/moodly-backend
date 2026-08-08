package com.tlavu.moodly.modules.auth.application;

import com.tlavu.moodly.modules.auth.domain.UserProfile;
import com.tlavu.moodly.modules.auth.infrastructure.UserProfileRepository;
import java.time.Instant;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Resolves the app user exclusively from the verified Auth0 JWT in the SecurityContext. */
@Component
public class CurrentUser {

	private final UserProfileRepository userProfileRepository;

	public CurrentUser(UserProfileRepository userProfileRepository) {
		this.userProfileRepository = userProfileRepository;
	}

	public String id() {
		var jwt = authenticatedJwt();
		ensureProfile(jwt);
		return jwt.getSubject();
	}

	private Jwt authenticatedJwt() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof Jwt jwt)) {
			throw new AccessDeniedException("An authenticated JWT is required.");
		}
		return jwt;
	}

	private void ensureProfile(Jwt jwt) {
		userProfileRepository.findByAuth0Subject(jwt.getSubject())
				.orElseGet(() -> createProfile(jwt));
	}

	private UserProfile createProfile(Jwt jwt) {
		var profile = new UserProfile(jwt.getSubject(), normalizedEmail(jwt), Instant.now());
		try {
			return userProfileRepository.save(profile);
		} catch (DuplicateKeyException exception) {
			return userProfileRepository.findByAuth0Subject(jwt.getSubject()).orElseThrow(() -> exception);
		}
	}

	private String normalizedEmail(Jwt jwt) {
		var email = jwt.getClaimAsString("email");
		if (email == null || email.isBlank()) {
			return null;
		}
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
