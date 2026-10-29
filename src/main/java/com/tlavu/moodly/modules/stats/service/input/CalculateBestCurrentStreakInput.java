package com.tlavu.moodly.modules.stats.service.input;

import java.time.LocalDate;
import java.util.List;

public record CalculateBestCurrentStreakInput(
		List<String> habitIds,
		LocalDate today
) {
	public CalculateBestCurrentStreakInput {
		habitIds = List.copyOf(habitIds);
	}
}
