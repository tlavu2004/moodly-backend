package com.tlavu.moodly.modules.stats.dto.response;

import com.tlavu.moodly.modules.stats.service.view.MostMissedHabitView;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"habitId", "missedCount"})
public record MostMissedHabitResponse(
		String habitId,
		long missedCount
) {
	public static MostMissedHabitResponse from(MostMissedHabitView view) {
		return new MostMissedHabitResponse(
				view.habitId(),
				view.missedCount()
		);
	}
}
