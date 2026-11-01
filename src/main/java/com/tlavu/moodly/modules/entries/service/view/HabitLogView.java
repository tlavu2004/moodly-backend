package com.tlavu.moodly.modules.entries.service.view;

import com.tlavu.moodly.modules.entries.model.HabitLog;

public record HabitLogView(
		String habitId,
		boolean done,
		String note
) {
	public static HabitLogView from(HabitLog log) {
		return new HabitLogView(
				log.getHabitId(),
				log.isDone(),
				log.getNote()
		);
	}
}
