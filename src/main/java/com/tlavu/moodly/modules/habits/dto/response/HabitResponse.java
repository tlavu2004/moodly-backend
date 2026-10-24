package com.tlavu.moodly.modules.habits.dto.response;

import com.tlavu.moodly.modules.habits.model.Habit;
import com.tlavu.moodly.modules.habits.model.TargetFrequency;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
		requiredProperties = {
			"id",
			"userId",
			"name",
			"targetFrequency",
			"active",
			"version"
		}
)
public record HabitResponse(
		String id,
		String userId,
		String name,
		@Schema(nullable = true) String icon,
		TargetFrequency targetFrequency,
		boolean active,
		Long version
) {

	public static HabitResponse from(Habit habit) {
		return new HabitResponse(
				habit.getId(),
				habit.getUserId(),
				habit.getName(),
				habit.getIcon(),
				habit.getTargetFrequency(),
				habit.isActive(),
				habit.getVersion()
		);
	}
}
