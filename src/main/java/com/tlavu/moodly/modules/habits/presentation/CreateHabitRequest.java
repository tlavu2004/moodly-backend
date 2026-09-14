package com.tlavu.moodly.modules.habits.presentation;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

public record CreateHabitRequest(
		@Schema(description = "Habit name", example = "Morning walk") @NotBlank String name,
		@Schema(description = "Optional icon or emoji", example = "🚶") String icon,
		@Schema(description = "Target completion frequency", example = "DAILY") @NotBlank String targetFrequency
) {
}
