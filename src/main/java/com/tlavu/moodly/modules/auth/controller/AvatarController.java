package com.tlavu.moodly.modules.auth.controller;

import com.tlavu.moodly.modules.auth.dto.request.ConfirmRequest;
import com.tlavu.moodly.modules.auth.dto.request.UploadRequest;
import com.tlavu.moodly.modules.auth.dto.response.AvatarResponse;
import com.tlavu.moodly.modules.auth.dto.response.UploadSignatureResponse;
import com.tlavu.moodly.modules.auth.dto.result.Avatar;
import com.tlavu.moodly.modules.auth.dto.result.UploadSignature;
import com.tlavu.moodly.modules.auth.service.AvatarService;
import com.tlavu.moodly.shared.controller.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me/avatar")
@Tag(name = "Avatar", description = "Avatar upload and retrieval for the authenticated user")
@RequiredArgsConstructor
public class AvatarController {

	private final AvatarService avatarService;

	@PostMapping("/upload-signature")
	@Operation(
			summary = "Create an avatar upload signature",
			description = "Creates a signed upload payload for Cloudinary after validating the image content type and size."
	)
	public ApiResponse<UploadSignatureResponse> signature(@Valid @RequestBody UploadRequest request) {

		UploadSignature signature = avatarService.createSignature(
			request.contentType(),
			request.sizeBytes()
		);
		return ApiResponse.success(UploadSignatureResponse.from(signature));
	}

	@PostMapping("/confirm")
	@Operation(
			summary = "Confirm an uploaded avatar",
			description = "Verifies the uploaded Cloudinary asset and saves it as the current user's avatar."
	)
	public ApiResponse<AvatarResponse> confirm(@Valid @RequestBody ConfirmRequest request) {

		Avatar avatar = avatarService.confirm(
			request.publicId(),
			request.version()
		);
		return ApiResponse.success(AvatarResponse.from(avatar));
	}

	@GetMapping
	@Operation(
			summary = "Get the current avatar",
			description = "Returns the authenticated user's current avatar metadata."
	)
	public ApiResponse<AvatarResponse> current() {

		return ApiResponse.success(AvatarResponse.from(avatarService.current()));
	}

	@DeleteMapping
	@Operation(
			summary = "Delete the current avatar",
			description = "Resets the avatar to the default and deletes the previous Cloudinary asset."
	)
	public ApiResponse<AvatarResponse> delete() {

		return ApiResponse.success(AvatarResponse.from(avatarService.delete()));
	}

}
