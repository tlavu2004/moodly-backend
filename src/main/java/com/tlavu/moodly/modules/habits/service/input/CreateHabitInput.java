package com.tlavu.moodly.modules.habits.service.input;

import com.tlavu.moodly.modules.habits.model.TargetFrequency;

public record CreateHabitInput(
		String name,
		String icon,
		TargetFrequency targetFrequency
) {}
