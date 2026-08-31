package com.tlavu.moodly.modules.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CurrentUserTest {

	private final CurrentUser currentUser = new CurrentUser();

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void resolvesTheVerifiedIdentityWithoutWritingApplicationData() {
		var jwt = Jwt.withTokenValue("test-token")
				.header("alg", "none")
				.subject("auth0|user-123")
				.claim("email", "  USER@Example.COM ")
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(60))
				.build();
		var authentication = new JwtAuthenticationToken(jwt);
		authentication.setAuthenticated(true);
		SecurityContextHolder.getContext().setAuthentication(authentication);
		assertThat(currentUser.id()).isEqualTo("auth0|user-123");
		assertThat(currentUser.identity()).isEqualTo(new CurrentUser.Identity("auth0|user-123", "user@example.com"));
	}
}
