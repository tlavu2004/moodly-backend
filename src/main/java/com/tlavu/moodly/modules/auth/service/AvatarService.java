package com.tlavu.moodly.modules.auth.service;

import com.tlavu.moodly.modules.auth.dto.result.Avatar;
import com.tlavu.moodly.modules.auth.dto.result.ConfirmedAsset;
import com.tlavu.moodly.modules.auth.dto.result.UploadSignature;
import com.tlavu.moodly.modules.auth.exception.AvatarException;
import com.tlavu.moodly.modules.auth.integration.cloudinary.contract.CloudinaryAssetClient;
import com.tlavu.moodly.modules.auth.model.PendingAssetDeletion;
import com.tlavu.moodly.modules.auth.model.PendingAvatarUpload;
import com.tlavu.moodly.modules.auth.model.UserProfile;
import com.tlavu.moodly.modules.auth.repository.PendingAssetDeletionRepository;
import com.tlavu.moodly.modules.auth.repository.PendingAvatarUploadRepository;
import com.tlavu.moodly.modules.auth.repository.UserProfileRepository;
import com.tlavu.moodly.modules.auth.security.CurrentUser;
import com.tlavu.moodly.shared.error.exception.InvalidRequestException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AvatarService {

	private static final long MAX_BYTES = 5L * 1024 * 1024;
	private static final long SIGNATURE_TTL_SECONDS = 3600;
	private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
	private final CurrentUser currentUser;
	private final UserProfileService userProfileService;
	private final UserProfileRepository profiles;
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
		this.cloudinary = cloudinary;
		this.pendingUploads = pendingUploads;
		this.pendingDeletions = pendingDeletions;
		this.cloudName = cloudName;
		this.apiKey = apiKey;
		this.apiSecret = apiSecret;
		this.uploadPreset = uploadPreset;
		this.folder = trimSlash(folder);
	}

	public UploadSignature createSignature(String contentType, long sizeBytes) {
		if (!ALLOWED_TYPES.contains(contentType)) {
			throw AvatarException.unsupportedContentType();
		}
		if (sizeBytes < 1 || sizeBytes > MAX_BYTES) {
			throw AvatarException.invalidFileSize();
		}

		userProfileService.synchronizeCurrent();

		long timestamp = Instant.now().getEpochSecond();
		String publicId = folder + "/users/" + subjectPath() + "/avatar/" + UUID.randomUUID();
		Instant expiresAt = Instant.now().plusSeconds(SIGNATURE_TTL_SECONDS);

		pendingUploads.save(
				new PendingAvatarUpload(
						publicId,
						currentUser.id(),
						expiresAt
				)
		);

		String toSign = "public_id=" + publicId + "&timestamp=" + timestamp + "&upload_preset=" + uploadPreset;

		return new UploadSignature(
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

	public Avatar confirm(String publicId, long version) {
		String subject = currentUser.id();
		if (!publicId.startsWith(folder + "/users/" + subjectPath(subject) + "/avatar/")) {
			throw AvatarException.uploadNotFound();
		}
		if (version < 1) {
			throw new InvalidRequestException("Avatar version must be positive.");
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

		UserProfile profile = profiles.findByAuth0Subject(subject).orElseThrow();
		String previousPublicId = profile.getAvatarPublicId();

		profile.replaceAvatar(
				asset.publicId(),
				asset.version(),
				asset.contentType(),
				asset.sizeBytes(),
				Instant.now()
		);
		profiles.save(profile);
		pendingUploads.delete(pending);

		if (
				previousPublicId != null &&
				!previousPublicId.equals(asset.publicId())
		) {
			deleteObsoleteAsset(previousPublicId);
		}

		return avatar(profile);
	}

	public Avatar current() {
		return profiles.findByAuth0Subject(currentUser.id())
				.map(this::avatar)
				.orElse(new Avatar(null, null, null, null));
	}

	public Avatar delete() {
		UserProfile profile = profiles.findByAuth0Subject(currentUser.id()).orElse(null);

		if (profile == null || profile.getAvatarPublicId() == null) {
			return new Avatar(
					null,
					null,
					null,
					null
			);
		}

		String previousPublicId = profile.getAvatarPublicId();
		profile.clearAvatar(Instant.now());
		profiles.save(profile);
		deleteObsoleteAsset(previousPublicId);

		return new Avatar(null, null, null, null);
	}

	private void deleteObsoleteAsset(String publicId) {

		try {
			cloudinary.deleteImage(publicId);
		} catch (RuntimeException exception) {

			try {
				PendingAssetDeletion pending = pendingDeletions.findByPublicId(publicId)
						.orElseGet(() -> new PendingAssetDeletion(publicId, Instant.now()));

				pending.recordFailure(Instant.now());
				pendingDeletions.save(pending);

				log.warn(
						"Cloudinary avatar cleanup queued after profile persistence ({})",
						exception.getClass().getSimpleName()
				);
			} catch (RuntimeException schedulingException) {
				log.error(
						"Cloudinary avatar cleanup could not be queued after profile persistence ({})",
						schedulingException.getClass().getSimpleName()
				);
			}
		}
	}

	private Avatar avatar(UserProfile profile) {

		if (profile.getAvatarPublicId() == null || profile.getAvatarVersion() == null) {
				return new Avatar(
						null,
						null,
						null,
						null
				);
		}

		return new Avatar(
				profile.getAvatarPublicId(),
				profile.getAvatarContentType(),
				profile.getAvatarSizeBytes(),
				"https://res.cloudinary.com/" + cloudName + "/image/upload/c_fill,g_auto,h_256,w_256,f_auto,q_auto/v" + profile.getAvatarVersion() + "/" + profile.getAvatarPublicId()
		);
	}

	private String subjectPath() {

		return subjectPath(currentUser.id());
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
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
