package com.tlavu.moodly.modules.e2e.controller;

import com.tlavu.moodly.modules.e2e.service.E2eUserResetService;
import com.tlavu.moodly.shared.application.exception.ForbiddenException;
import com.tlavu.moodly.shared.presentation.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Local browser test helper. The target identity is fixed in server code and cannot be supplied by clients. */
@RestController
@RequestMapping("/internal/e2e")
@Profile("!prod & !production")
@ConditionalOnProperty(name = "moodly.e2e.reset.enabled", havingValue = "true")
public class E2eResetController {

	private final E2eUserResetService resetService;
	private final byte[] resetToken;

	public E2eResetController(
			E2eUserResetService resetService,
			@Value("${moodly.e2e.reset.token:}") String resetToken
	) {
		this.resetService = resetService;
		this.resetToken = resetToken.getBytes(StandardCharsets.UTF_8);
	}

	@PostMapping("/reset")
	@Operation(summary = "Reset the fixed E2E account", description = "Resets and seeds the server-owned synthetic Playwright account. Enabled only when explicitly configured outside production.")
	public ResponseEntity<ApiResponse<E2eUserResetService.ResetResult>> reset(
			@RequestHeader(value = "X-E2E-Reset-Token", required = false) String suppliedToken
	) {
		if (resetToken.length == 0 || suppliedToken == null || !MessageDigest.isEqual(
				resetToken, suppliedToken.getBytes(StandardCharsets.UTF_8))) {
			throw new ForbiddenException();
		}
		return ResponseEntity.ok(ApiResponse.success(resetService.reset()));
	}
}
