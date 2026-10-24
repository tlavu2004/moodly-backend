package com.tlavu.moodly.modules.habits.service.command;

import com.tlavu.moodly.modules.habits.model.TargetFrequency;

public record CreateHabitCommand(
		String name,
		String icon,
		TargetFrequency targetFrequency
) {}
