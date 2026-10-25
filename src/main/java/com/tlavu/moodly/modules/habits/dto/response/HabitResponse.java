package com.tlavu.moodly.modules.habits.dto.response;

import com.tlavu.moodly.modules.habits.service.view.HabitView;
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
		@Schema(allowableValues = "DAILY") String targetFrequency,
		boolean active,
		Long version
) {

	public static HabitResponse from(HabitView habit) {
		return new HabitResponse(
				habit.id(),
				habit.userId(),
				habit.name(),
				habit.icon(),
				habit.targetFrequency().name(),
				habit.active(),
				habit.version()
		);
	}
}
