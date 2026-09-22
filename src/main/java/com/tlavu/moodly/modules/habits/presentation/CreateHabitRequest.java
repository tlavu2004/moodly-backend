package com.tlavu.moodly.modules.habits.presentation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateHabitRequest(
		@Schema(description = "Habit name", example = "Morning walk") @NotBlank String name,
		@Schema(description = "Optional icon or emoji", example = "🚶") String icon,
		@Schema(
				description = "Target completion frequency. Moodly currently supports daily habits only.",
				example = "DAILY",
				allowableValues = "DAILY"
		)
		@NotNull
		@Pattern(regexp = "DAILY", message = "must be DAILY")
		String targetFrequency
) {
}
