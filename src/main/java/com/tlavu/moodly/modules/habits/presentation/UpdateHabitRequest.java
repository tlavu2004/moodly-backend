package com.tlavu.moodly.modules.habits.presentation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateHabitRequest(
		@Schema(description = "Habit name", example = "Evening walk") @NotBlank String name,
		@Schema(description = "Optional icon or emoji", example = "🚶") String icon,
		@Schema(description = "Version returned by the latest read", example = "0")
		@NotNull @PositiveOrZero Long version
) {
}
