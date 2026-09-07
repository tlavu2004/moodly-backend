package com.tlavu.moodly.modules.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tlavu.moodly.modules.auth.domain.PendingAvatarUpload;
import com.tlavu.moodly.modules.auth.domain.UserProfile;
import com.tlavu.moodly.modules.auth.infrastructure.CloudinaryAssetClient;
import com.tlavu.moodly.modules.auth.infrastructure.PendingAvatarUploadRepository;
import com.tlavu.moodly.modules.auth.infrastructure.UserProfileRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AvatarServiceTest {

	private static final String SUBJECT = "auth0|user-123";
	private static final String NAMESPACE = "moodly/test/users/auth0_user-123/avatar/";

	@Mock private CurrentUser currentUser;
	@Mock private UserProfileService userProfileService;
	@Mock private UserProfileRepository profiles;
	@Mock private CloudinaryAssetClient cloudinary;
	@Mock private PendingAvatarUploadRepository pendingUploads;
	private AvatarService service;

	@BeforeEach
	void setUp() {
		service = new AvatarService(currentUser, userProfileService, profiles, cloudinary, pendingUploads,
				"test-cloud", "test-key", "test-secret", "test-preset", "moodly/test");
	}

	@Test
	void createsAnOwnerScopedSignedUploadPayload() throws Exception {
		when(currentUser.id()).thenReturn(SUBJECT);
		var payload = service.createSignature("image/png", 1024);

		assertThat(payload.publicId()).startsWith(NAMESPACE);
		assertThat(payload.uploadUrl()).isEqualTo("https://api.cloudinary.com/v1_1/test-cloud/image/upload");
		assertThat(payload.signature()).isEqualTo(sha1("public_id=" + payload.publicId()
				+ "&timestamp=" + payload.timestamp() + "&upload_preset=test-presettest-secret"));
		verify(userProfileService).synchronizeCurrent();
		verify(pendingUploads).save(any(PendingAvatarUpload.class));
	}

	@Test
	void rejectsUnsupportedOrOversizedAvatarBeforeCreatingAPendingUpload() {
		assertThatThrownBy(() -> service.createSignature("image/gif", 1024))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.createSignature("image/png", 5L * 1024 * 1024 + 1))
				.isInstanceOf(IllegalArgumentException.class);

		verify(userProfileService, never()).synchronizeCurrent();
		verify(pendingUploads, never()).save(any());
	}

	@Test
	void confirmsOwnedVerifiedAssetAndDeletesTheReplacedAvatar() {
		var publicId = NAMESPACE + "new-avatar";
		when(currentUser.id()).thenReturn(SUBJECT);
		var profile = new UserProfile(SUBJECT, "user@example.com", Instant.now());
		profile.replaceAvatar(NAMESPACE + "old-avatar", 3, "image/png", 100, Instant.now());
		when(pendingUploads.findByPublicIdAndAuth0Subject(publicId, SUBJECT))
				.thenReturn(Optional.of(new PendingAvatarUpload(publicId, SUBJECT, Instant.now().plusSeconds(60))));
		when(cloudinary.findImage(publicId))
				.thenReturn(new CloudinaryAssetClient.ConfirmedAsset(publicId, 4, "image/webp", 2048));
		when(profiles.findByAuth0Subject(SUBJECT)).thenReturn(Optional.of(profile));

		var avatar = service.confirm(publicId, 4);

		assertThat(avatar.publicId()).isEqualTo(publicId);
		assertThat(avatar.deliveryUrl()).contains("/v4/" + publicId);
		assertThat(profile.getAvatarContentType()).isEqualTo("image/webp");
		verify(profiles).save(profile);
		verify(pendingUploads).delete(any(PendingAvatarUpload.class));
		verify(cloudinary).deleteImage(NAMESPACE + "old-avatar");
	}

	@Test
	void rejectsAnotherUsersAvatarBeforeCloudinaryLookup() {
		when(currentUser.id()).thenReturn(SUBJECT);
		assertThatThrownBy(() -> service.confirm("moodly/test/users/auth0_other/avatar/asset", 1))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("does not belong");

		verify(cloudinary, never()).findImage(any());
		verify(profiles, never()).save(any());
	}

	@Test
	void rejectsCloudinaryMetadataThatViolatesAvatarPolicy() {
		var publicId = NAMESPACE + "oversized";
		when(currentUser.id()).thenReturn(SUBJECT);
		when(pendingUploads.findByPublicIdAndAuth0Subject(publicId, SUBJECT))
				.thenReturn(Optional.of(new PendingAvatarUpload(publicId, SUBJECT, Instant.now().plusSeconds(60))));
		when(cloudinary.findImage(publicId))
				.thenReturn(new CloudinaryAssetClient.ConfirmedAsset(publicId, 1, "image/png", 5L * 1024 * 1024 + 1));

		assertThatThrownBy(() -> service.confirm(publicId, 1))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("metadata is invalid");

		verify(profiles, never()).save(any());
		verify(pendingUploads, never()).delete(any());
	}

	@Test
	void rejectsAnUnknownExpiredOrVersionMismatchedConfirmationWithoutChangingTheProfile() {
		var publicId = NAMESPACE + "expired";
		when(currentUser.id()).thenReturn(SUBJECT);
		when(pendingUploads.findByPublicIdAndAuth0Subject(publicId, SUBJECT))
				.thenReturn(Optional.of(new PendingAvatarUpload(publicId, SUBJECT, Instant.now().minusSeconds(1))));

		assertThatThrownBy(() -> service.confirm(publicId, 1))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("unknown or has expired");
		verify(cloudinary, never()).findImage(any());
		verify(profiles, never()).save(any());
	}

	@Test
	void returnsEmptyAvatarMetadataUntilTheAuthenticatedUserHasConfirmedAnAvatar() {
		when(currentUser.id()).thenReturn(SUBJECT);
		when(profiles.findByAuth0Subject(SUBJECT)).thenReturn(Optional.empty());

		assertThat(service.current()).isEqualTo(new AvatarService.Avatar(null, null, null, null));
	}

	private static String sha1(String value) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8)));
	}
}
