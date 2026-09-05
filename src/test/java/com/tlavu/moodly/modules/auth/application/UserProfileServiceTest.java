package com.tlavu.moodly.modules.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tlavu.moodly.modules.auth.domain.UserProfile;
import com.tlavu.moodly.modules.auth.infrastructure.UserProfileRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

	@Mock
	private CurrentUser currentUser;
	@Mock
	private UserProfileRepository profiles;
	@InjectMocks
	private UserProfileService service;

	@Test
	void explicitlyCreatesAProfileFromTheVerifiedIdentity() {
		when(currentUser.identity()).thenReturn(new CurrentUser.Identity("auth0|user-123", "user@example.com"));
		when(profiles.findByAuth0Subject("auth0|user-123")).thenReturn(Optional.empty());
		when(profiles.save(any(UserProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

		var result = service.synchronizeCurrent();

		var saved = ArgumentCaptor.forClass(UserProfile.class);
		verify(profiles).save(saved.capture());
		assertThat(result).isSameAs(saved.getValue());
		assertThat(result.getAuth0Subject()).isEqualTo("auth0|user-123");
		assertThat(result.getEmail()).isEqualTo("user@example.com");
	}

	@Test
	void reusesAnExistingProfileIdempotently() {
		var existing = new UserProfile("auth0|user-123", "user@example.com", Instant.now());
		when(currentUser.identity()).thenReturn(new CurrentUser.Identity("auth0|user-123", "user@example.com"));
		when(profiles.findByAuth0Subject("auth0|user-123")).thenReturn(Optional.of(existing));

		assertThat(service.synchronizeCurrent()).isSameAs(existing);
		verify(profiles, never()).save(any());
	}
}
