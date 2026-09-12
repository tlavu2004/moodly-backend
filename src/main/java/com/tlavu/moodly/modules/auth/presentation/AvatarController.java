package com.tlavu.moodly.modules.auth.presentation;

import com.tlavu.moodly.modules.auth.application.AvatarService;
import com.tlavu.moodly.shared.presentation.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me/avatar")
@Tag(name = "Avatar", description = "Avatar upload and retrieval for the authenticated user")
public class AvatarController {
	private final AvatarService avatarService;
	public AvatarController(AvatarService avatarService) { this.avatarService = avatarService; }
	@PostMapping("/upload-signature")
	@Operation(summary = "Create an avatar upload signature", description = "Creates a signed upload payload for Cloudinary after validating the image content type and size.")
	public ApiResponse<AvatarService.UploadSignature> signature(@Valid @RequestBody UploadRequest request) { return ApiResponse.success(avatarService.createSignature(request.contentType(), request.sizeBytes())); }
	@PostMapping("/confirm")
	@Operation(summary = "Confirm an uploaded avatar", description = "Verifies the uploaded Cloudinary asset and saves it as the current user's avatar.")
	public ApiResponse<AvatarService.Avatar> confirm(@Valid @RequestBody ConfirmRequest request) { return ApiResponse.success(avatarService.confirm(request.publicId(), request.version())); }
	@GetMapping
	@Operation(summary = "Get the current avatar", description = "Returns the authenticated user's current avatar metadata.")
	public ApiResponse<AvatarService.Avatar> current() { return ApiResponse.success(avatarService.current()); }
	public record UploadRequest(@NotBlank String contentType, @Positive long sizeBytes) {}
	public record ConfirmRequest(@NotBlank String publicId, @Positive long version) {}
}
