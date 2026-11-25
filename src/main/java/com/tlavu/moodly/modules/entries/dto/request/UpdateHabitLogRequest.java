package com.tlavu.moodly.modules.entries.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

public record UpdateHabitLogRequest(
		@Schema(
				description = "ID of the habit to update",
				example = "66d34a8e7eb1e840cb8a85c1"
		) @NotBlank String habitId,

		@Schema(
				description = "Whether the habit was completed today",
				example = "true"
		) @NotNull Boolean done,

		@Schema(
				description = "Replacement note for this log. Omit it or send null to clear the note.",
				nullable = true,
				example = "Walked for 30 minutes."
		) String note
) {}
