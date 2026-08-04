package com.tlavu.moodly.modules.entries.api;

import jakarta.validation.constraints.NotBlank;

public record UpdateHabitLogRequest(
		@NotBlank String habitId,
		boolean done,
		String note
) {
}
