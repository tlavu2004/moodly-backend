package com.tlavu.moodly.modules.cdc.presentation;

import com.tlavu.moodly.modules.cdc.application.DailyEntryReindexService;
import com.tlavu.moodly.modules.cdc.application.CdcDeliveryService;
import com.tlavu.moodly.modules.cdc.infrastructure.CdcDeadLetterRepository;
import com.tlavu.moodly.shared.application.exception.ForbiddenException;
import com.tlavu.moodly.shared.application.exception.ResourceNotFoundException;
import com.tlavu.moodly.shared.presentation.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Temporary maintenance guard; replace this header with an admin authority in Phase 3. */
@RestController
@RequestMapping("/internal/cdc")
@Tag(name = "CDC maintenance", description = "Internal operations protected by the X-Maintenance-Key header")
public class CdcMaintenanceController {

	private final DailyEntryReindexService reindexService;
	private final CdcDeadLetterRepository deadLetterRepository;
	private final CdcDeliveryService deliveryService;
	private final byte[] maintenanceKey;

	public CdcMaintenanceController(
			DailyEntryReindexService reindexService,
			CdcDeadLetterRepository deadLetterRepository,
			CdcDeliveryService deliveryService,
			@Value("${moodly.cdc.maintenance-key:}") String maintenanceKey
	) {
		this.reindexService = reindexService;
		this.deadLetterRepository = deadLetterRepository;
		this.deliveryService = deliveryService;
		this.maintenanceKey = maintenanceKey.getBytes(StandardCharsets.UTF_8);
	}

	@PostMapping("/reindex")
	@Operation(
			summary = "Reindex daily entries",
			description = "Rebuilds the daily-entry search index. Requires the X-Maintenance-Key header.",
			security = @SecurityRequirement(name = "maintenanceKey")
	)
	public ResponseEntity<ApiResponse<DailyEntryReindexService.ReindexResult>> reindex(
			@Parameter(description = "Internal CDC maintenance key.", required = true)
			@RequestHeader(value = "X-Maintenance-Key", required = false) String suppliedKey
	) {
		if (hasInvalidMaintenanceKey(suppliedKey)) {
			throw new ForbiddenException();
		}
		return ResponseEntity.ok(ApiResponse.success(reindexService.reindex()));
	}

	@PostMapping("/dead-letters/{id}/replay")
	@Operation(
			summary = "Replay a CDC dead letter",
			description = "Replays a failed CDC delivery by ID. Requires the X-Maintenance-Key header.",
			security = @SecurityRequirement(name = "maintenanceKey")
	)
	@io.swagger.v3.oas.annotations.responses.ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
					responseCode = "204",
					description = "CDC dead letter replayed successfully."
			),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
					responseCode = "404",
					ref = "#/components/responses/NotFound"
			)
	})
	public ResponseEntity<Void> replay(
			@org.springframework.web.bind.annotation.PathVariable String id,
			@Parameter(description = "Internal CDC maintenance key.", required = true)
			@RequestHeader(value = "X-Maintenance-Key", required = false) String suppliedKey
	) {
		if (hasInvalidMaintenanceKey(suppliedKey)) {
			throw new ForbiddenException();
		}
		var deadLetter = deadLetterRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("CDC dead letter was not found."));
		deliveryService.replay(deadLetter);
		return ResponseEntity.noContent().build();
	}

	private boolean hasInvalidMaintenanceKey(String suppliedKey) {
		return maintenanceKey.length == 0 || suppliedKey == null || !MessageDigest.isEqual(
				maintenanceKey,
				suppliedKey.getBytes(StandardCharsets.UTF_8)
		);
	}
}
