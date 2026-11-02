package com.tlavu.moodly.modules.auth.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tlavu.moodly.modules.auth.model.PendingAssetDeletion;
import com.tlavu.moodly.modules.auth.exception.AvatarAssetDeletionException;
import com.tlavu.moodly.modules.auth.integration.cloudinary.port.CloudinaryAssetClient;
import com.tlavu.moodly.modules.auth.repository.PendingAssetDeletionRepository;
import com.tlavu.moodly.modules.auth.repository.UserProfileRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PendingAssetDeletionCleanupTest {

	@Mock private PendingAssetDeletionRepository pendingDeletions;
	@Mock private UserProfileRepository profiles;
	@Mock private CloudinaryAssetClient cloudinary;
	@InjectMocks private PendingAssetDeletionCleanup cleanup;

	@Test
	void removesTheCleanupTaskAfterCloudinaryDeletionSucceeds() {
		PendingAssetDeletion pending = new PendingAssetDeletion(
				"moodly/test/users/user/avatar/obsolete", Instant.now().minusSeconds(1));
		when(pendingDeletions.findByNextAttemptAtBefore(any())).thenReturn(List.of(pending));

		cleanup.cleanupObsoleteAssets();

		verify(cloudinary).deleteImage(pending.getPublicId());
		verify(pendingDeletions).delete(pending);
		verify(pendingDeletions, never()).save(any());
	}

	@Test
	void retainsAndReschedulesTheCleanupTaskWhenCloudinaryDeletionFails() {
		PendingAssetDeletion pending = new PendingAssetDeletion(
				"moodly/test/users/user/avatar/obsolete", Instant.now().minusSeconds(1));
		when(pendingDeletions.findByNextAttemptAtBefore(any())).thenReturn(List.of(pending));
		doThrow(new AvatarAssetDeletionException("Cloudinary unavailable")).when(cloudinary).deleteImage(pending.getPublicId());

		cleanup.cleanupObsoleteAssets();

		ArgumentCaptor<PendingAssetDeletion> saved = ArgumentCaptor.forClass(PendingAssetDeletion.class);
		verify(pendingDeletions).save(saved.capture());
		assertThat(saved.getValue().getCleanupAttempts()).isEqualTo(1);
		assertThat(saved.getValue().getNextAttemptAt()).isAfter(Instant.now());
		verify(pendingDeletions, never()).delete(any());
	}

	@Test
	void continuesWithOtherAssetsAfterUnexpectedDeletionFailure() {
		PendingAssetDeletion failing = new PendingAssetDeletion("moodly/test/users/user/avatar/failing", Instant.now().minusSeconds(1));
		PendingAssetDeletion healthy = new PendingAssetDeletion("moodly/test/users/user/avatar/healthy", Instant.now().minusSeconds(1));
		when(pendingDeletions.findByNextAttemptAtBefore(any())).thenReturn(List.of(failing, healthy));
		doThrow(new IllegalArgumentException("invalid client state")).when(cloudinary).deleteImage(failing.getPublicId());

		cleanup.cleanupObsoleteAssets();

		verify(pendingDeletions).save(failing);
		assertThat(failing.getCleanupAttempts()).isEqualTo(1);
		verify(cloudinary).deleteImage(healthy.getPublicId());
		verify(pendingDeletions).delete(healthy);
	}

	@Test
	void retainsQueuedAssetWhileAProfileStillReferencesIt() {
		PendingAssetDeletion pending = new PendingAssetDeletion(
				"moodly/test/users/user/avatar/active", Instant.now().minusSeconds(1));
		when(pendingDeletions.findByNextAttemptAtBefore(any())).thenReturn(List.of(pending));
		when(profiles.existsByAvatarPublicId(pending.getPublicId())).thenReturn(true);

		cleanup.cleanupObsoleteAssets();

		verify(cloudinary, never()).deleteImage(pending.getPublicId());
		verify(pendingDeletions, never()).delete(pending);
		verify(pendingDeletions).save(pending);
		assertThat(pending.getNextAttemptAt()).isAfter(Instant.now());
	}

	@Test
	void removesExpiredTaskForAnAssetThatIsStillActiveWithoutDeletingTheAsset() {
		PendingAssetDeletion pending = new PendingAssetDeletion(
				"moodly/test/users/user/avatar/active", Instant.now().minusSeconds(1));
		org.springframework.test.util.ReflectionTestUtils.setField(
				pending, "createdAt", Instant.now().minusSeconds(25 * 3600));
		when(pendingDeletions.findByNextAttemptAtBefore(any())).thenReturn(List.of(pending));
		when(profiles.existsByAvatarPublicId(pending.getPublicId())).thenReturn(true);

		cleanup.cleanupObsoleteAssets();

		verify(pendingDeletions).delete(pending);
		verify(cloudinary, never()).deleteImage(pending.getPublicId());
	}
}
