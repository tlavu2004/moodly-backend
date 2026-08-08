package com.tlavu.moodly.modules.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tlavu.moodly.modules.auth.domain.UserProfile;
import com.tlavu.moodly.modules.auth.infrastructure.UserProfileRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class CurrentUserTest {

	@Mock
	private UserProfileRepository userProfileRepository;
	@InjectMocks
	private CurrentUser currentUser;

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void usesTheJwtSubjectAndCreatesANormalizedProfileOnFirstRequest() {
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
		when(userProfileRepository.findByAuth0Subject("auth0|user-123")).thenReturn(Optional.empty());
		when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

		assertThat(currentUser.id()).isEqualTo("auth0|user-123");

		var profile = ArgumentCaptor.forClass(UserProfile.class);
		verify(userProfileRepository).save(profile.capture());
		assertThat(profile.getValue().getAuth0Subject()).isEqualTo("auth0|user-123");
		assertThat(profile.getValue().getEmail()).isEqualTo("user@example.com");
	}
}
