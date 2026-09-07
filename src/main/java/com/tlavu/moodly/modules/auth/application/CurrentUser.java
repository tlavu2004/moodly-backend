package com.tlavu.moodly.modules.auth.application;

import java.util.Locale;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Resolves the app user exclusively from the verified Auth0 JWT in the SecurityContext. */
@Component
public class CurrentUser {

	public String id() {
		return authenticatedJwt().getSubject();
	}

	public Identity identity() {
		var jwt = authenticatedJwt();
		return new Identity(jwt.getSubject(), normalizedEmail(jwt));
	}

	private Jwt authenticatedJwt() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof Jwt jwt)) {
			throw new AccessDeniedException("An authenticated JWT is required.");
		}
		return jwt;
	}

	private String normalizedEmail(Jwt jwt) {
		var email = jwt.getClaimAsString("email");
		if (email == null || email.isBlank()) {
			return null;
		}
		return email.trim().toLowerCase(Locale.ROOT);
	}

	public record Identity(String subject, String email) {
	}
}
