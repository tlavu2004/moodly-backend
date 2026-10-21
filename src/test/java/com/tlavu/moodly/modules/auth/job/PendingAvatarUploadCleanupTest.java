package com.tlavu.moodly.modules.auth.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tlavu.moodly.modules.auth.model.PendingAvatarUpload;
import com.tlavu.moodly.modules.auth.integration.cloudinary.contract.CloudinaryAssetClient;
import com.tlavu.moodly.modules.auth.repository.PendingAvatarUploadRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PendingAvatarUploadCleanupTest {

	@Mock private PendingAvatarUploadRepository pendingUploads;
	@Mock private CloudinaryAssetClient cloudinary;
	@InjectMocks private PendingAvatarUploadCleanup cleanup;

	@Test
	void deletesExpiredUnconfirmedAssetsAndTheirPendingRecords() {
		PendingAvatarUpload pending = new PendingAvatarUpload(
				"moodly/test/users/user/avatar/expired", "user", Instant.now().minusSeconds(1));
		when(pendingUploads.findByExpiresAtBefore(any())).thenReturn(List.of(pending));

		cleanup.cleanupExpiredUploads();

		verify(cloudinary).deleteImage(pending.getPublicId());
		verify(pendingUploads).delete(pending);
	}

	@Test
	void recordsTheFailureSoAnExpiredAssetCanBeRetried() {
		PendingAvatarUpload pending = new PendingAvatarUpload(
				"moodly/test/users/user/avatar/retry", "user", Instant.now().minusSeconds(1));
		when(pendingUploads.findByExpiresAtBefore(any())).thenReturn(List.of(pending));
		doThrow(new IllegalStateException("Cloudinary unavailable")).when(cloudinary).deleteImage(pending.getPublicId());

		cleanup.cleanupExpiredUploads();

		ArgumentCaptor<PendingAvatarUpload> saved = ArgumentCaptor.forClass(PendingAvatarUpload.class);
		verify(pendingUploads).save(saved.capture());
		org.assertj.core.api.Assertions.assertThat(saved.getValue().getCleanupAttempts()).isEqualTo(1);
	}
}
