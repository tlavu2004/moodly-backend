package com.tlavu.moodly.modules.auth.presentation;

import com.tlavu.moodly.modules.auth.application.AvatarService;
import com.tlavu.moodly.shared.presentation.dto.response.ApiResponse;
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
public class AvatarController {
	private final AvatarService avatarService;
	public AvatarController(AvatarService avatarService) { this.avatarService = avatarService; }
	@PostMapping("/upload-signature") public ApiResponse<AvatarService.UploadSignature> signature(@Valid @RequestBody UploadRequest request) { return ApiResponse.success(avatarService.createSignature(request.contentType(), request.sizeBytes())); }
	@PostMapping("/confirm") public ApiResponse<AvatarService.Avatar> confirm(@Valid @RequestBody ConfirmRequest request) { return ApiResponse.success(avatarService.confirm(request.publicId(), request.version())); }
	@GetMapping public ApiResponse<AvatarService.Avatar> current() { return ApiResponse.success(avatarService.current()); }
	public record UploadRequest(@NotBlank String contentType, @Positive long sizeBytes) {}
	public record ConfirmRequest(@NotBlank String publicId, @Positive long version) {}
}
