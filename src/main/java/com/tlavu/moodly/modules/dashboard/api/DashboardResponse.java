package com.tlavu.moodly.modules.dashboard.api;

import com.tlavu.moodly.modules.entries.domain.DailyEntry;
import com.tlavu.moodly.modules.habits.domain.Habit;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"activeHabits", "completedHabitCount", "totalHabitCount", "completionRatio", "weeklyMood", "bestCurrentStreak"})
public record DashboardResponse(
		@Schema(nullable = true) DailyEntry todayEntry,
		List<Habit> activeHabits,
		int completedHabitCount,
		int totalHabitCount,
		double completionRatio,
		WeeklyMoodSummary weeklyMood,
		int bestCurrentStreak
) {

	@Schema(requiredProperties = {"entryCount"})
	public record WeeklyMoodSummary(@Schema(nullable = true) Double averageScore, long entryCount) {
	}

}
