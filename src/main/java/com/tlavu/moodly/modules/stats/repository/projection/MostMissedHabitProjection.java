package com.tlavu.moodly.modules.stats.repository.projection;

public record MostMissedHabitProjection(
		String habitId,
		long missedCount
) {}
