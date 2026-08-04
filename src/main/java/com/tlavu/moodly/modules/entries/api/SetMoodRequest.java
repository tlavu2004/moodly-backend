package com.tlavu.moodly.modules.entries.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record SetMoodRequest(
		@Min(1) @Max(5) int score,
		java.util.List<String> tags,
		String note
) {
}
