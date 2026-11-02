package com.tlavu.moodly.modules.auth.job;

import com.tlavu.moodly.shared.error.ExceptionLogSummary;

import com.tlavu.moodly.modules.auth.integration.cloudinary.port.CloudinaryAssetClient;
import com.tlavu.moodly.modules.auth.exception.AvatarAssetDeletionException;
import com.tlavu.moodly.modules.auth.model.PendingAssetDeletion;
import com.tlavu.moodly.modules.auth.repository.PendingAssetDeletionRepository;
import com.tlavu.moodly.modules.auth.repository.UserProfileRepository;
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
	private final UserProfileRepository profiles;
	private final CloudinaryAssetClient cloudinary;

	@Scheduled(fixedDelayString = "${moodly.cloudinary.pending-cleanup-delay-ms:300000}")
	public void cleanupObsoleteAssets() {

		for (PendingAssetDeletion pending : pendingDeletions.findByNextAttemptAtBefore(Instant.now())) {
			try {
				if (profiles.existsByAvatarPublicId(pending.getPublicId())) {
					if (pending.recordStillActive(Instant.now())) {
						pendingDeletions.save(pending);
					} else {
						pendingDeletions.delete(pending);
					}
					continue;
				}
				cloudinary.deleteImage(pending.getPublicId());
				pendingDeletions.delete(pending);
			} catch (RuntimeException exception) {
				pending.recordFailure(Instant.now());
				try {
					pendingDeletions.save(pending);
					if (exception instanceof AvatarAssetDeletionException || exception instanceof DataAccessException) {
						log.warn("Cloudinary avatar cleanup retry failed: pendingId={}, attempt={}, cause={}", pending.getId(), pending.getCleanupAttempts(), ExceptionLogSummary.of(exception));
					} else {
						log.error("Unexpected Cloudinary avatar cleanup retry failure: pendingId={}, attempt={}, cause={}", pending.getId(), pending.getCleanupAttempts(), ExceptionLogSummary.of(exception));
					}
				} catch (RuntimeException persistenceException) {
					log.error(
							"Cloudinary avatar cleanup retry state could not be persisted: pendingId={}, cause={}",
							pending.getId(),
							ExceptionLogSummary.of(persistenceException)
					);
				}
			}
		}
	}
}
