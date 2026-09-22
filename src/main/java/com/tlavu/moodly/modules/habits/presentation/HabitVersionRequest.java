package com.tlavu.moodly.modules.habits.presentation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record HabitVersionRequest(
		@Schema(description = "Version returned by the latest read", example = "0")
		@NotNull @PositiveOrZero Long version
) {
}
