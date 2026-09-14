package com.tlavu.moodly.modules.entries.presentation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.media.Schema;

public record SetMoodRequest(
		@Schema(description = "Mood score, from 1 (lowest) to 5 (highest)", example = "4") @Min(1) @Max(5) int score,
		@Schema(description = "Optional labels describing the mood", example = "[\"productive\", \"calm\"]") java.util.List<String> tags,
		@Schema(description = "Optional note for the day", example = "Finished the release plan.") String note
) {
}
