package com.tlavu.moodly.modules.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tlavu.moodly.modules.auth.integration.cloudinary.contract.CloudinaryAssetClient;
import com.tlavu.moodly.modules.auth.integration.cloudinary.contract.ConfirmedAsset;
import com.tlavu.moodly.modules.auth.repository.PendingAvatarUploadRepository;
import com.tlavu.moodly.modules.auth.repository.UserProfileRepository;
import com.tlavu.moodly.support.MongoTestConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MongoTestConfiguration.class)
class AuthApiIntegrationTest {
	private static final String USER_ID = "__test_phase1_api_user__";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private UserProfileRepository userProfileRepository;
	@Autowired
	private PendingAvatarUploadRepository pendingAvatarUploadRepository;
	@MockitoBean
	private CloudinaryAssetClient cloudinary;

	@AfterEach
	void cleanUp() {
		userProfileRepository.deleteAll();
		pendingAvatarUploadRepository.deleteAll();
	}

	@Test
	void requiresAuthenticationForEveryAvatarEndpoint() throws Exception {
		mockMvc.perform(get("/me/avatar"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
		mockMvc.perform(post("/me/avatar/upload-signature")
					.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
		mockMvc.perform(post("/me/avatar/confirm")
					.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/me/avatar"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void confirmsOnlyTheAuthenticatedUsersPendingAvatar() throws Exception {
		String userA = "auth0|avatar-owner";
		String userB = "auth0|avatar-other";
		RequestPostProcessor ownerToken = jwt().jwt(token -> token.subject(userA).claim("email", "owner@example.com"));
		RequestPostProcessor otherToken = jwt().jwt(token -> token.subject(userB).claim("email", "other@example.com"));

		String signatureResponse = mockMvc.perform(post("/me/avatar/upload-signature").with(ownerToken)
					.contentType(MediaType.APPLICATION_JSON).content("{\"contentType\":\"image/png\",\"sizeBytes\":1024}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.publicId").value(org.hamcrest.Matchers.startsWith("moodly/test/users/auth0_avatar-owner/avatar/")))
				.andExpect(jsonPath("$.data.cloudName").value("test-cloud"))
				.andExpect(jsonPath("$.data.apiKey").value("test-api-key"))
				.andExpect(jsonPath("$.data.uploadPreset").value("test-upload-preset"))
				.andExpect(jsonPath("$.data.timestamp").isNumber())
				.andExpect(jsonPath("$.data.expiresAt").isNotEmpty())
				.andExpect(jsonPath("$.data.signature").isNotEmpty())
				.andExpect(jsonPath("$.data.uploadUrl").isNotEmpty())
				.andExpect(jsonPath("$.data.apiSecret").doesNotExist())
				.andReturn().getResponse().getContentAsString();
		String publicId = new tools.jackson.databind.ObjectMapper().readTree(signatureResponse).path("data").path("publicId").asString();

		mockMvc.perform(post("/me/avatar/confirm").with(otherToken)
					.contentType(MediaType.APPLICATION_JSON).content("{\"publicId\":\"" + publicId + "\",\"version\":1}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("AVATAR_UPLOAD_NOT_FOUND"));
		verify(cloudinary, never()).findImage(publicId);

		org.mockito.Mockito.when(cloudinary.findImage(publicId))
				.thenReturn(new ConfirmedAsset(publicId, 1, "image/png", 1024));
		mockMvc.perform(post("/me/avatar/confirm").with(ownerToken)
					.contentType(MediaType.APPLICATION_JSON).content("{\"publicId\":\"" + publicId + "\",\"version\":1}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.publicId").value(publicId))
				.andExpect(jsonPath("$.data.contentType").value("image/png"))
				.andExpect(jsonPath("$.data.sizeBytes").value(1024))
				.andExpect(jsonPath("$.data.deliveryUrl").value(org.hamcrest.Matchers.containsString("/c_fill,g_auto,h_256,w_256,f_auto,q_auto/v1/")));
		mockMvc.perform(get("/me/avatar").with(ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.publicId").value(publicId));

		verify(cloudinary).findImage(publicId);
	}

	@Test
	void rejectsInvalidAvatarDeclarationsForAnAuthenticatedUser() throws Exception {
		mockMvc.perform(post("/me/avatar/upload-signature")
					.with(jwt().jwt(token -> token.subject(USER_ID)))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"contentType\":\"image/gif\",\"sizeBytes\":1024}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("AVATAR_CONTENT_TYPE_UNSUPPORTED"));
	}

	@Test
	void bootstrapsTheAuth0ProfileExplicitlyAndIdempotently() throws Exception {
		RequestPostProcessor authenticated = jwt().jwt(token -> token.subject(USER_ID).claim("email", "  API@Example.COM "));

		mockMvc.perform(put("/auth/profile").with(authenticated))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.userId").value(USER_ID))
				.andExpect(jsonPath("$.data.email").value("api@example.com"));

		mockMvc.perform(put("/auth/profile").with(authenticated))
				.andExpect(status().isOk());

		assertThat(userProfileRepository.count()).isEqualTo(1);
	}

	@Test
	void ordinaryProtectedRequestsDoNotCreateAProfileAsASideEffect() throws Exception {
		mockMvc.perform(get("/habits").with(jwt().jwt(token -> token.subject(USER_ID))))
				.andExpect(status().isOk());

		assertThat(userProfileRepository.count()).isZero();
	}
}
