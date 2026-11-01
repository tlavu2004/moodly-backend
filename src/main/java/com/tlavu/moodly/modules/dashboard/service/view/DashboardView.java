package com.tlavu.moodly.modules.dashboard.service.view;

import com.tlavu.moodly.modules.entries.service.view.DailyEntryView;
import com.tlavu.moodly.modules.habits.service.view.HabitView;
import java.util.List;

public record DashboardView(
		DailyEntryView todayEntry,
		List<HabitView> activeHabits,
		int completedHabitCount,
		int totalHabitCount,
		double completionRatio,
		WeeklyMoodView weeklyMood,
		int bestCurrentStreak
) {
	public DashboardView {
		activeHabits = List.copyOf(activeHabits);
	}
}
