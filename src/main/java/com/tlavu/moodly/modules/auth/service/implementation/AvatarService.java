package com.tlavu.moodly.modules.auth.service.implementation;

import com.tlavu.moodly.shared.error.ExceptionLogSummary;

import com.tlavu.moodly.modules.auth.exception.AvatarException;
import com.tlavu.moodly.modules.auth.exception.AvatarAssetDeletionException;
import com.tlavu.moodly.modules.auth.exception.AvatarProfileMissingException;
import com.tlavu.moodly.modules.auth.integration.cloudinary.model.ConfirmedAsset;
import com.tlavu.moodly.modules.auth.integration.cloudinary.port.CloudinaryAssetClient;
import com.tlavu.moodly.modules.auth.model.PendingAssetDeletion;
import com.tlavu.moodly.modules.auth.model.PendingAvatarUpload;
import com.tlavu.moodly.modules.auth.model.UserProfile;
import com.tlavu.moodly.modules.auth.repository.PendingAssetDeletionRepository;
import com.tlavu.moodly.modules.auth.repository.PendingAvatarUploadRepository;
import com.tlavu.moodly.modules.auth.repository.UserProfileRepository;
import com.tlavu.moodly.modules.auth.security.context.CurrentUser;
import com.tlavu.moodly.modules.auth.service.input.ConfirmAvatarInput;
import com.tlavu.moodly.modules.auth.service.input.CreateUploadSignatureInput;
import com.tlavu.moodly.modules.auth.service.view.AvatarView;
import com.tlavu.moodly.modules.auth.service.view.UploadSignatureView;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;

@Slf4j
@Service
public class AvatarService {
	private static final int MAX_AVATAR_WRITE_ATTEMPTS = 5;
	private static final long MAX_BYTES = 5L * 1024 * 1024;
	private static final long SIGNATURE_TTL_SECONDS = 3600;
	private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
	private final CurrentUser currentUser;
	private final UserProfileService userProfileService;
	private final UserProfileRepository profiles;
	private final AvatarProfileWriter avatarProfileWriter;
	private final CloudinaryAssetClient cloudinary;
	private final PendingAvatarUploadRepository pendingUploads;
	private final PendingAssetDeletionRepository pendingDeletions;
	private final String cloudName;
	private final String apiKey;
	private final String apiSecret;
	private final String uploadPreset;
	private final String folder;

	public AvatarService(
			CurrentUser currentUser,
			UserProfileService userProfileService,
			UserProfileRepository profiles,
			AvatarProfileWriter avatarProfileWriter,
			CloudinaryAssetClient cloudinary,
			PendingAvatarUploadRepository pendingUploads,
			PendingAssetDeletionRepository pendingDeletions,
			@Value("${moodly.cloudinary.cloud-name}") String cloudName,
			@Value("${moodly.cloudinary.api-key}") String apiKey,
			@Value("${moodly.cloudinary.api-secret}") String apiSecret,
			@Value("${moodly.cloudinary.upload-preset}") String uploadPreset,
			@Value("${moodly.cloudinary.folder}") String folder
	) {
		this.currentUser = currentUser;
		this.userProfileService = userProfileService;
		this.profiles = profiles;
		this.avatarProfileWriter = avatarProfileWriter;
		this.cloudinary = cloudinary;
		this.pendingUploads = pendingUploads;
		this.pendingDeletions = pendingDeletions;
		this.cloudName = cloudName;
		this.apiKey = apiKey;
		this.apiSecret = apiSecret;
		this.uploadPreset = uploadPreset;
		this.folder = trimSlash(folder);
	}

	public UploadSignatureView createSignature(CreateUploadSignatureInput input) {
		String contentType = input.contentType();
		long sizeBytes = input.sizeBytes();

		if (!ALLOWED_TYPES.contains(contentType)) {
			throw AvatarException.unsupportedContentType();
		}
		if (sizeBytes < 1 || sizeBytes > MAX_BYTES) {
			throw AvatarException.invalidFileSize();
		}

		userProfileService.synchronizeCurrent();

		long timestamp = Instant.now().getEpochSecond();
		String publicId = folder + "/users/" + subjectPath(currentUser.id()) + "/avatar/" + UUID.randomUUID();
		Instant expiresAt = Instant.now().plusSeconds(SIGNATURE_TTL_SECONDS);

		pendingUploads.save(
				new PendingAvatarUpload(
						publicId,
						currentUser.id(),
						expiresAt
				)
		);

		String toSign = "public_id=" + publicId +
				"&timestamp=" + timestamp +
				"&upload_preset=" + uploadPreset;

		return new UploadSignatureView(
				cloudName,
				apiKey,
				uploadPreset,
				publicId,
				timestamp,
				expiresAt,
				sha1(toSign + apiSecret),
				"https://api.cloudinary.com/v1_1/" + cloudName + "/image/upload"
		);
	}

	public AvatarView confirm(ConfirmAvatarInput input) {
		String publicId = input.publicId();
		long version = input.version();
		String subject = currentUser.id();

		if (!publicId.startsWith(folder + "/users/" + subjectPath(subject) + "/avatar/")) {
			throw AvatarException.uploadNotFound();
		}
		if (version < 1) {
			throw AvatarException.invalidVersion();
		}

		PendingAvatarUpload pending = pendingUploads.findByPublicIdAndAuth0Subject(publicId, subject)
				.filter(upload -> upload.getExpiresAt().isAfter(Instant.now()))
				.orElseThrow(AvatarException::uploadExpired);
		ConfirmedAsset asset = cloudinary.findImage(publicId);

		if (asset.version() != version) {
			throw AvatarException.versionMismatch();
		}
		if (
				!publicId.equals(asset.publicId()) ||
				!ALLOWED_TYPES.contains(asset.contentType()) ||
				asset.sizeBytes() > MAX_BYTES
		) {
			throw AvatarException.invalidMetadata();
		}

		for (int attempt = 0; attempt < MAX_AVATAR_WRITE_ATTEMPTS; attempt++) {
			UserProfile profile = profiles.findByAuth0Subject(subject)
					.orElseThrow(AvatarProfileMissingException::new);
			String previousPublicId = profile.getAvatarPublicId();
			PendingAssetDeletion obsolete = previousPublicId != null && !previousPublicId.equals(asset.publicId())
					? queueObsoleteAsset(previousPublicId)
					: null;
			profile.replaceAvatar(asset.publicId(), asset.version(), asset.contentType(), asset.sizeBytes(), Instant.now());
			if (!avatarProfileWriter.replaceIfCurrent(profile, previousPublicId)) {
				continue;
			}
			pendingUploads.delete(pending);
			if (obsolete != null) {
				deleteObsoleteAsset(obsolete);
			}
			return avatar(profile);
		}
		throw AvatarException.conflict();
	}

	public AvatarView current() {
		return profiles.findByAuth0Subject(currentUser.id())
				.map(this::avatar)
				.orElse(new AvatarView(null, null, null, null));
	}

	public AvatarView delete() {
		for (int attempt = 0; attempt < MAX_AVATAR_WRITE_ATTEMPTS; attempt++) {
			UserProfile profile = profiles.findByAuth0Subject(currentUser.id()).orElse(null);
			if (profile == null || profile.getAvatarPublicId() == null) {
				return new AvatarView(null, null, null, null);
			}
			String previousPublicId = profile.getAvatarPublicId();
			PendingAssetDeletion obsolete = queueObsoleteAsset(previousPublicId);
			profile.clearAvatar(Instant.now());
			if (!avatarProfileWriter.clearIfCurrent(profile, previousPublicId)) {
				continue;
			}
			deleteObsoleteAsset(obsolete);
			return new AvatarView(null, null, null, null);
		}
		throw AvatarException.conflict();
	}

	private PendingAssetDeletion queueObsoleteAsset(String publicId) {
		return pendingDeletions.findByPublicId(publicId).orElseGet(() -> {
			PendingAssetDeletion pending = new PendingAssetDeletion(publicId, Instant.now().plusSeconds(60));
			try {
				pendingDeletions.save(pending);
				return pending;
			} catch (DuplicateKeyException race) {
				return pendingDeletions.findByPublicId(publicId)
						.orElseThrow(() -> new IllegalStateException(
								"Avatar cleanup task collided without a task for the same asset", race));
			}
		});
	}

	private void deleteObsoleteAsset(PendingAssetDeletion pending) {
		try {
			if (profiles.existsByAvatarPublicId(pending.getPublicId())) {
				return;
			}
			cloudinary.deleteImage(pending.getPublicId());
			pendingDeletions.delete(pending);
		} catch (RuntimeException exception) {
			try {
				pending.recordFailure(Instant.now());
				pendingDeletions.save(pending);

				if (exception instanceof AvatarAssetDeletionException) {
					log.warn("Cloudinary avatar cleanup queued after profile persistence: pendingId={}, cause={}", pending.getId(), ExceptionLogSummary.of(exception));
				} else {
					log.error("Unexpected Cloudinary avatar cleanup failure queued after profile persistence: pendingId={}, cause={}", pending.getId(), ExceptionLogSummary.of(exception));
				}
			} catch (RuntimeException schedulingException) {
				schedulingException.addSuppressed(exception);
				log.error("Cloudinary avatar cleanup could not be queued after profile persistence: pendingId={}, cause={}", pending.getId(), ExceptionLogSummary.of(schedulingException));
			}
		}
	}

	private AvatarView avatar(UserProfile profile) {
		if (profile.getAvatarPublicId() == null || profile.getAvatarVersion() == null) {
			return new AvatarView(
						null,
						null,
						null,
						null
				);
		}

		return new AvatarView(
				profile.getAvatarPublicId(),
				profile.getAvatarContentType(),
				profile.getAvatarSizeBytes(),
				"https://res.cloudinary.com/" + cloudName + "/image/upload/c_fill,g_auto,h_256,w_256,f_auto,q_auto/v" + profile.getAvatarVersion() + "/" + profile.getAvatarPublicId()
		);
	}

	private static String subjectPath(String subject) {
		return subject.replaceAll("[^A-Za-z0-9_-]", "_");
	}

	private static String trimSlash(String value) {
		return value.replaceAll("^/+|/+$", "");
	}

	private static String sha1(String value) {
		try {
			return HexFormat.of()
					.formatHex(MessageDigest.getInstance("SHA-1")
					.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
