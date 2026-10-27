package com.tlavu.moodly.modules.stats.service.view;

import com.tlavu.moodly.modules.stats.repository.projection.MostMissedHabitProjection;

public record MostMissedHabitView(String habitId, long missedCount) {
	public static MostMissedHabitView from(MostMissedHabitProjection projection) {
		return new MostMissedHabitView(projection.habitId(), projection.missedCount());
	}
}
