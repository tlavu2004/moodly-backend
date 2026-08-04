package com.tlavu.moodly.modules.habits.api;

import jakarta.validation.constraints.NotBlank;

public record CreateHabitRequest(
		@NotBlank String name,
		String icon,
		@NotBlank String targetFrequency
) {
}
