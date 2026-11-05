package com.tlavu.moodly.modules.auth.job;

import com.tlavu.moodly.shared.error.logging.ExceptionLogSummary;

import com.tlavu.moodly.modules.auth.integration.cloudinary.port.CloudinaryAssetClient;
import com.tlavu.moodly.modules.auth.exception.AvatarAssetDeletionException;
import com.tlavu.moodly.modules.auth.model.PendingAvatarUpload;
import com.tlavu.moodly.modules.auth.repository.PendingAvatarUploadRepository;
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
@ConditionalOnProperty(
		name = "moodly.cloudinary.pending-cleanup.enabled",
		havingValue = "true",
		matchIfMissing = true
)
public class PendingAvatarUploadCleanup {
	private final PendingAvatarUploadRepository pendingUploads;
	private final UserProfileRepository profiles;
	private final CloudinaryAssetClient cloudinary;

	@Scheduled(fixedDelayString = "${moodly.cloudinary.pending-cleanup-delay-ms:300000}")
	public void cleanupExpiredUploads() {
		for (PendingAvatarUpload pending : pendingUploads.findByExpiresAtBefore(Instant.now())) {
			try {
				if (!profiles.existsByAvatarPublicId(pending.getPublicId())) {
					cloudinary.deleteImage(pending.getPublicId());
				}

				pendingUploads.delete(pending);
			} catch (RuntimeException exception) {
				pending.recordCleanupFailure();

				try {
					pendingUploads.save(pending);
					if (exception instanceof AvatarAssetDeletionException || exception instanceof DataAccessException) {
						log.warn(
								"Avatar cleanup retry failed: pendingId={}, attempt={}, cause={}",
								pending.getId(),
								pending.getCleanupAttempts(),
								ExceptionLogSummary.of(exception)
						);
					} else {
						log.error(
								"Unexpected avatar cleanup retry failure: pendingId={}, attempt={}, cause={}",
								pending.getId(),
								pending.getCleanupAttempts(),
								ExceptionLogSummary.of(exception)
						);
					}
				} catch (RuntimeException persistenceException) {
					persistenceException.addSuppressed(exception);

					log.error(
							"Avatar cleanup retry state could not be persisted: pendingId={}, cause={}",
							pending.getId(),
							ExceptionLogSummary.of(persistenceException)
					);
				}
			}
		}
	}
}
