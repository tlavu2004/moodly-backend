package com.tlavu.moodly.modules.dashboard.api;

import com.tlavu.moodly.modules.entries.domain.DailyEntry;
import com.tlavu.moodly.modules.habits.domain.Habit;
import java.util.List;

public record DashboardResponse(
		DailyEntry todayEntry,
		List<Habit> activeHabits,
		int completedHabitCount,
		int totalHabitCount,
		double completionRatio,
		WeeklyMoodSummary weeklyMood,
		int bestCurrentStreak
) {

	public record WeeklyMoodSummary(Double averageScore, long entryCount) {
	}

}
