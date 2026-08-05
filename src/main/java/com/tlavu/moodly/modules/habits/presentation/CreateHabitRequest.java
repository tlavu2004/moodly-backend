package com.tlavu.moodly.modules.habits.presentation;

import jakarta.validation.constraints.NotBlank;

public record CreateHabitRequest(
		@NotBlank String name,
		String icon,
		@NotBlank String targetFrequency
) {
}
