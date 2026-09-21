package com.tlavu.moodly.modules.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tlavu.moodly.modules.auth.domain.PendingAssetDeletion;
import com.tlavu.moodly.modules.auth.infrastructure.CloudinaryAssetClient;
import com.tlavu.moodly.modules.auth.infrastructure.PendingAssetDeletionRepository;
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
	@Mock private CloudinaryAssetClient cloudinary;
	@InjectMocks private PendingAssetDeletionCleanup cleanup;

	@Test
	void removesTheCleanupTaskAfterCloudinaryDeletionSucceeds() {
		var pending = new PendingAssetDeletion("moodly/test/users/user/avatar/obsolete", Instant.now().minusSeconds(1));
		when(pendingDeletions.findByNextAttemptAtBefore(any())).thenReturn(List.of(pending));

		cleanup.cleanupObsoleteAssets();

		verify(cloudinary).deleteImage(pending.getPublicId());
		verify(pendingDeletions).delete(pending);
		verify(pendingDeletions, never()).save(any());
	}

	@Test
	void retainsAndReschedulesTheCleanupTaskWhenCloudinaryDeletionFails() {
		var pending = new PendingAssetDeletion("moodly/test/users/user/avatar/obsolete", Instant.now().minusSeconds(1));
		when(pendingDeletions.findByNextAttemptAtBefore(any())).thenReturn(List.of(pending));
		doThrow(new IllegalStateException("Cloudinary unavailable")).when(cloudinary).deleteImage(pending.getPublicId());

		cleanup.cleanupObsoleteAssets();

		var saved = ArgumentCaptor.forClass(PendingAssetDeletion.class);
		verify(pendingDeletions).save(saved.capture());
		assertThat(saved.getValue().getCleanupAttempts()).isEqualTo(1);
		assertThat(saved.getValue().getNextAttemptAt()).isAfter(Instant.now());
		verify(pendingDeletions, never()).delete(any());
	}
}
