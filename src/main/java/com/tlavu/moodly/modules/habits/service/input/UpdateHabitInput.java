package com.tlavu.moodly.modules.habits.service.input;

public record UpdateHabitInput(
		String name,
		String icon,
		long version
) {}
