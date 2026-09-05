package com.tlavu.moodly.modules.auth.application;

import com.tlavu.moodly.modules.auth.domain.UserProfile;
import com.tlavu.moodly.modules.auth.infrastructure.UserProfileRepository;
import com.tlavu.moodly.modules.auth.infrastructure.CloudinaryAssetClient;
import com.tlavu.moodly.modules.auth.domain.PendingAvatarUpload;
import com.tlavu.moodly.modules.auth.infrastructure.PendingAvatarUploadRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AvatarService {
	private static final long MAX_BYTES = 5L * 1024 * 1024;
	private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
	private final CurrentUser currentUser;
	private final UserProfileService userProfileService;
	private final UserProfileRepository profiles;
	private final CloudinaryAssetClient cloudinary;
	private final PendingAvatarUploadRepository pendingUploads;
	private final String cloudName;
	private final String apiKey;
	private final String apiSecret;
	private final String uploadPreset;
	private final String folder;

	public AvatarService(CurrentUser currentUser, UserProfileService userProfileService, UserProfileRepository profiles, CloudinaryAssetClient cloudinary, PendingAvatarUploadRepository pendingUploads,
			@Value("${moodly.cloudinary.cloud-name}") String cloudName,
			@Value("${moodly.cloudinary.api-key}") String apiKey,
			@Value("${moodly.cloudinary.api-secret}") String apiSecret,
			@Value("${moodly.cloudinary.upload-preset}") String uploadPreset,
			@Value("${moodly.cloudinary.folder}") String folder) {
		this.currentUser = currentUser; this.userProfileService = userProfileService; this.profiles = profiles; this.cloudinary = cloudinary; this.pendingUploads = pendingUploads; this.cloudName = cloudName; this.apiKey = apiKey;
		this.apiSecret = apiSecret; this.uploadPreset = uploadPreset; this.folder = trimSlash(folder);
	}

	public UploadSignature createSignature(String contentType, long sizeBytes) {
		if (!ALLOWED_TYPES.contains(contentType) || sizeBytes < 1 || sizeBytes > MAX_BYTES) throw new IllegalArgumentException("Avatar must be a JPG, PNG, or WebP image no larger than 5 MiB.");
		userProfileService.synchronizeCurrent();
		var timestamp = Instant.now().getEpochSecond();
		var publicId = folder + "/users/" + subjectPath() + "/avatar/" + UUID.randomUUID();
		pendingUploads.save(new PendingAvatarUpload(publicId, currentUser.id(), Instant.now().plusSeconds(3600)));
		var toSign = "public_id=" + publicId + "&timestamp=" + timestamp + "&upload_preset=" + uploadPreset;
		return new UploadSignature(cloudName, apiKey, uploadPreset, publicId, timestamp, sha1(toSign + apiSecret),
				"https://api.cloudinary.com/v1_1/" + cloudName + "/image/upload");
	}

	public Avatar confirm(String publicId, long ignoredVersion) {
		var subject = currentUser.id();
		if (!publicId.startsWith(folder + "/users/" + subjectPath(subject) + "/avatar/")) throw new IllegalArgumentException("Avatar asset does not belong to the authenticated user.");
		if (ignoredVersion < 1) throw new IllegalArgumentException("Avatar version must be positive.");
		var pending = pendingUploads.findByPublicIdAndAuth0Subject(publicId, subject)
				.filter(upload -> upload.getExpiresAt().isAfter(Instant.now()))
				.orElseThrow(() -> new IllegalArgumentException("Avatar upload is unknown or has expired."));
		var asset = cloudinary.findImage(publicId);
		if (!publicId.equals(asset.publicId()) || !ALLOWED_TYPES.contains(asset.contentType()) || asset.sizeBytes() > MAX_BYTES || asset.version() < 1) throw new IllegalArgumentException("Cloudinary avatar metadata is invalid.");
		var profile = profiles.findByAuth0Subject(subject).orElseThrow();
		var previousPublicId = profile.getAvatarPublicId();
		profile.replaceAvatar(asset.publicId(), asset.version(), asset.contentType(), asset.sizeBytes(), Instant.now());
		profiles.save(profile);
		pendingUploads.delete(pending);
		if (previousPublicId != null && !previousPublicId.equals(asset.publicId())) cloudinary.deleteImage(previousPublicId);
		return avatar(profile);
	}

	public Avatar current() {
		return profiles.findByAuth0Subject(currentUser.id()).map(this::avatar).orElse(new Avatar(null, null, null, null));
	}

	private Avatar avatar(UserProfile profile) {
		if (profile.getAvatarPublicId() == null || profile.getAvatarVersion() == null) return new Avatar(null, null, null, null);
		return new Avatar(profile.getAvatarPublicId(), profile.getAvatarContentType(), profile.getAvatarSizeBytes(), "https://res.cloudinary.com/" + cloudName + "/image/upload/c_fill,g_auto,h_256,w_256,f_auto,q_auto/v" + profile.getAvatarVersion() + "/" + profile.getAvatarPublicId());
	}
	private String subjectPath() { return subjectPath(currentUser.id()); }
	private static String subjectPath(String subject) { return subject.replaceAll("[^A-Za-z0-9_-]", "_"); }
	private static String trimSlash(String value) { return value.replaceAll("^/+|/+$", ""); }
	private static String sha1(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
	public record UploadSignature(String cloudName, String apiKey, String uploadPreset, String publicId, long timestamp, String signature, String uploadUrl) {}
	public record Avatar(String publicId, String contentType, Long sizeBytes, String deliveryUrl) {}
}
