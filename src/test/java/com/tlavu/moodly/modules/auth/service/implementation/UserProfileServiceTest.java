package com.tlavu.moodly.modules.auth.service.implementation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tlavu.moodly.modules.auth.model.UserProfile;
import com.tlavu.moodly.modules.auth.repository.UserProfileRepository;
import com.tlavu.moodly.modules.auth.security.CurrentUser;
import com.tlavu.moodly.modules.auth.security.CurrentUserIdentity;
import com.tlavu.moodly.modules.auth.service.view.ProfileView;
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
		when(currentUser.identity()).thenReturn(new CurrentUserIdentity("auth0|user-123", "user@example.com"));
		when(profiles.findByAuth0Subject("auth0|user-123")).thenReturn(Optional.empty());
		when(profiles.save(any(UserProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ProfileView result = service.synchronizeCurrent();

		ArgumentCaptor<UserProfile> saved = ArgumentCaptor.forClass(UserProfile.class);
		verify(profiles).save(saved.capture());
		assertThat(result.userId()).isEqualTo(saved.getValue().getAuth0Subject());
		assertThat(result.email()).isEqualTo("user@example.com");
		Instant synchronizedAt = result.updatedAt();
		saved.getValue().clearAvatar(Instant.parse("2030-01-01T00:00:00Z"));
		assertThat(result.updatedAt()).isEqualTo(synchronizedAt);
	}

	@Test
	void reusesAnExistingProfileIdempotently() {
		UserProfile existing = new UserProfile("auth0|user-123", "user@example.com", Instant.now());
		when(currentUser.identity()).thenReturn(new CurrentUserIdentity("auth0|user-123", "user@example.com"));
		when(profiles.findByAuth0Subject("auth0|user-123")).thenReturn(Optional.of(existing));

		assertThat(service.synchronizeCurrent()).isEqualTo(ProfileView.from(existing));
		verify(profiles, never()).save(any());
	}
}
