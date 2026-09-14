package com.tlavu.moodly.modules.entries.presentation;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

public record UpdateHabitLogRequest(
		@Schema(description = "ID of the habit to update", example = "66d34a8e7eb1e840cb8a85c1") @NotBlank String habitId,
		@Schema(description = "Whether the habit was completed today", example = "true") boolean done,
		@Schema(description = "Optional completion note", example = "Walked for 30 minutes.") String note
) {
}
