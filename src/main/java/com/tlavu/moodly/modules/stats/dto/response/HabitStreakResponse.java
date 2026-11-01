package com.tlavu.moodly.modules.stats.dto.response;

import com.tlavu.moodly.modules.stats.service.view.HabitStreakView;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"habitId", "currentStreak"})
public record HabitStreakResponse(
		String habitId,
		int currentStreak
) {
	public static HabitStreakResponse from(HabitStreakView view) {
		return new HabitStreakResponse(
				view.habitId(),
				view.currentStreak()
		);
	}
}
