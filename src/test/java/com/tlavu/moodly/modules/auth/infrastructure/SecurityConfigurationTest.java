package com.tlavu.moodly.modules.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;

class SecurityConfigurationTest {

	private static final String ISSUER = "https://moodly-test.auth0.com/";
	private static final String AUDIENCE = "https://api.moodly.test";
	private final SecurityConfiguration configuration = new SecurityConfiguration();

	@Test
	void acceptsATokenWithTheConfiguredIssuerAudienceAndLifetime() {
		var validator = new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefaultWithIssuer(ISSUER), configuration.audienceValidator(AUDIENCE));

		assertThat(validator.validate(token(ISSUER, List.of(AUDIENCE), Instant.now().plusSeconds(60))).hasErrors()).isFalse();
	}

	@Test
	void rejectsWrongIssuerWrongAudienceAndExpiredTokens() {
		var validator = new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefaultWithIssuer(ISSUER), configuration.audienceValidator(AUDIENCE));

		assertThat(validator.validate(token("https://other.auth0.com/", List.of(AUDIENCE), Instant.now().plusSeconds(60))).hasErrors()).isTrue();
		assertThat(validator.validate(token(ISSUER, List.of("https://wrong-audience"), Instant.now().plusSeconds(60))).hasErrors()).isTrue();
		assertThat(validator.validate(token(ISSUER, List.of(AUDIENCE), Instant.now().minusSeconds(120))).hasErrors()).isTrue();
	}

	private Jwt token(String issuer, List<String> audience, Instant expiresAt) {
		return Jwt.withTokenValue("test-token")
				.header("alg", "RS256")
				.subject("auth0|user-123")
				.issuer(issuer)
				.audience(audience)
				.issuedAt(expiresAt.minusSeconds(60))
				.expiresAt(expiresAt)
				.build();
	}
}
