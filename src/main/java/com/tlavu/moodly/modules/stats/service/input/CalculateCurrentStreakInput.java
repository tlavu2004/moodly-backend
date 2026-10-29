package com.tlavu.moodly.modules.stats.service.input;

import java.time.LocalDate;

public record CalculateCurrentStreakInput(
		String habitId,
		LocalDate today
) {}
