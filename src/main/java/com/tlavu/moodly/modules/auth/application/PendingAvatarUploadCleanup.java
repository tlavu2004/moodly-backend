package com.tlavu.moodly.modules.auth.application;

import com.tlavu.moodly.modules.auth.infrastructure.CloudinaryAssetClient;
import com.tlavu.moodly.modules.auth.infrastructure.PendingAvatarUploadRepository;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PendingAvatarUploadCleanup {
	private final PendingAvatarUploadRepository pendingUploads;
	private final CloudinaryAssetClient cloudinary;
	public PendingAvatarUploadCleanup(PendingAvatarUploadRepository pendingUploads, CloudinaryAssetClient cloudinary) { this.pendingUploads = pendingUploads; this.cloudinary = cloudinary; }
	@Scheduled(fixedDelayString = "${moodly.cloudinary.pending-cleanup-delay-ms:300000}")
	public void cleanupExpiredUploads() {
		for (var pending : pendingUploads.findByExpiresAtBefore(Instant.now())) {
			try { cloudinary.deleteImage(pending.getPublicId()); pendingUploads.delete(pending); }
			catch (RuntimeException exception) { pending.recordCleanupFailure(); pendingUploads.save(pending); log.warn("Avatar cleanup retry {} failed for {}", pending.getCleanupAttempts(), pending.getPublicId()); }
		}
	}
}
