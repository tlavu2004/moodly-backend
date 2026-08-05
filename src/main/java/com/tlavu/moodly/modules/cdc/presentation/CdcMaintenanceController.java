package com.tlavu.moodly.modules.cdc.presentation;

import com.tlavu.moodly.modules.cdc.application.DailyEntryReindexService;
import com.tlavu.moodly.modules.cdc.application.CdcDeliveryService;
import com.tlavu.moodly.modules.cdc.infrastructure.CdcDeadLetterRepository;
import com.tlavu.moodly.shared.presentation.dto.response.ApiResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Temporary maintenance guard; replace this header with an admin authority in Phase 3. */
@RestController
@RequestMapping("/internal/cdc")
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
	public ResponseEntity<ApiResponse<DailyEntryReindexService.ReindexResult>> reindex(
			@RequestHeader(value = "X-Maintenance-Key", required = false) String suppliedKey
	) throws IOException {
		if (hasInvalidMaintenanceKey(suppliedKey)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		return ResponseEntity.ok(ApiResponse.success(reindexService.reindex()));
	}

	@PostMapping("/dead-letters/{id}/replay")
	public ResponseEntity<Void> replay(
			@org.springframework.web.bind.annotation.PathVariable String id,
			@RequestHeader(value = "X-Maintenance-Key", required = false) String suppliedKey
	) {
		if (hasInvalidMaintenanceKey(suppliedKey)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		var deadLetter = deadLetterRepository.findById(id).orElse(null);
		if (deadLetter == null) {
			return ResponseEntity.notFound().build();
		}
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
