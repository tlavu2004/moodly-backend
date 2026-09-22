package com.tlavu.moodly.modules.auth.application;

import com.tlavu.moodly.modules.auth.infrastructure.CloudinaryAssetClient;
import com.tlavu.moodly.modules.auth.infrastructure.PendingAssetDeletionRepository;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "moodly.cloudinary.pending-cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class PendingAssetDeletionCleanup {
	private final PendingAssetDeletionRepository pendingDeletions;
	private final CloudinaryAssetClient cloudinary;

	public PendingAssetDeletionCleanup(PendingAssetDeletionRepository pendingDeletions, CloudinaryAssetClient cloudinary) {
		this.pendingDeletions = pendingDeletions;
		this.cloudinary = cloudinary;
	}

	@Scheduled(fixedDelayString = "${moodly.cloudinary.pending-cleanup-delay-ms:300000}")
	public void cleanupObsoleteAssets() {
		for (var pending : pendingDeletions.findByNextAttemptAtBefore(Instant.now())) {
			try {
				cloudinary.deleteImage(pending.getPublicId());
				pendingDeletions.delete(pending);
			} catch (RuntimeException exception) {
				pending.recordFailure(Instant.now());
				try {
					pendingDeletions.save(pending);
					log.warn("Cloudinary avatar cleanup retry {} failed ({})", pending.getCleanupAttempts(), exception.getClass().getSimpleName());
				} catch (RuntimeException persistenceException) {
					log.error("Cloudinary avatar cleanup retry state could not be persisted ({})",
							persistenceException.getClass().getSimpleName());
				}
			}
		}
	}
}
