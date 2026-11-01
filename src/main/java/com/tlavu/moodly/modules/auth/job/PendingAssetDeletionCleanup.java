package com.tlavu.moodly.modules.auth.job;

import com.tlavu.moodly.modules.auth.integration.cloudinary.port.CloudinaryAssetClient;
import com.tlavu.moodly.modules.auth.exception.AvatarAssetDeletionException;
import com.tlavu.moodly.modules.auth.model.PendingAssetDeletion;
import com.tlavu.moodly.modules.auth.repository.PendingAssetDeletionRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.dao.DataAccessException;

@Slf4j
@RequiredArgsConstructor
@Component
@ConditionalOnProperty(name = "moodly.cloudinary.pending-cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class PendingAssetDeletionCleanup {
	private final PendingAssetDeletionRepository pendingDeletions;
	private final CloudinaryAssetClient cloudinary;

	@Scheduled(fixedDelayString = "${moodly.cloudinary.pending-cleanup-delay-ms:300000}")
	public void cleanupObsoleteAssets() {

		for (PendingAssetDeletion pending : pendingDeletions.findByNextAttemptAtBefore(Instant.now())) {
			try {
				cloudinary.deleteImage(pending.getPublicId());
				pendingDeletions.delete(pending);
			} catch (RuntimeException exception) {
				pending.recordFailure(Instant.now());
				try {
					pendingDeletions.save(pending);
					if (exception instanceof AvatarAssetDeletionException || exception instanceof DataAccessException) {
						log.warn("Cloudinary avatar cleanup retry {} failed", pending.getCleanupAttempts(), exception);
					} else {
						log.error("Unexpected Cloudinary avatar cleanup retry {} failure", pending.getCleanupAttempts(), exception);
					}
				} catch (RuntimeException persistenceException) {
					log.error(
							"Cloudinary avatar cleanup retry state could not be persisted ({})",
							persistenceException.getClass().getSimpleName()
					);
				}
			}
		}
	}
}
