package com.tlavu.moodly.modules.habits.service.view;

import com.tlavu.moodly.modules.habits.model.Habit;
import com.tlavu.moodly.modules.habits.domain.TargetFrequency;

public record HabitView(
		String id,
		String userId,
		String name,
		String icon,
		TargetFrequency targetFrequency,
		boolean active,
		Long version
) {
	public static HabitView from(Habit habit) {
		return new HabitView(
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
