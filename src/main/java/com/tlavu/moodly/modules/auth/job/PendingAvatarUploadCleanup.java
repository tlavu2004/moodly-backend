package com.tlavu.moodly.modules.auth.job;

import com.tlavu.moodly.modules.auth.integration.cloudinary.contract.CloudinaryAssetClient;
import com.tlavu.moodly.modules.auth.model.PendingAvatarUpload;
import com.tlavu.moodly.modules.auth.repository.PendingAvatarUploadRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
@ConditionalOnProperty(
		name = "moodly.cloudinary.pending-cleanup.enabled",
		havingValue = "true",
		matchIfMissing = true
)
public class PendingAvatarUploadCleanup {
	private final PendingAvatarUploadRepository pendingUploads;
	private final CloudinaryAssetClient cloudinary;

	@Scheduled(fixedDelayString = "${moodly.cloudinary.pending-cleanup-delay-ms:300000}")
	public void cleanupExpiredUploads() {
		for (PendingAvatarUpload pending : pendingUploads.findByExpiresAtBefore(Instant.now())) {
			try {
				cloudinary.deleteImage(pending.getPublicId());
				pendingUploads.delete(pending);
			} catch (RuntimeException exception) {
				pending.recordCleanupFailure();
				try {
					pendingUploads.save(pending);
					log.warn(
							"Avatar cleanup retry {} failed ({})",
							pending.getCleanupAttempts(),
							exception.getClass().getSimpleName()
					);
				} catch (RuntimeException persistenceException) {
					log.error(
							"Avatar cleanup retry state could not be persisted ({})",
							persistenceException.getClass().getSimpleName()
					);
				}
			}
		}
	}
}
