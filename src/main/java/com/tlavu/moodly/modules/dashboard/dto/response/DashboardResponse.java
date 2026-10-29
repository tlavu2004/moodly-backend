package com.tlavu.moodly.modules.dashboard.dto.response;

import com.tlavu.moodly.modules.dashboard.service.view.DashboardView;
import com.tlavu.moodly.modules.dashboard.service.view.WeeklyMoodView;
import com.tlavu.moodly.modules.entries.dto.response.DailyEntryResponse;
import com.tlavu.moodly.modules.habits.dto.response.HabitResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(
		requiredProperties = {
			"activeHabits",
			"completedHabitCount",
			"totalHabitCount",
			"completionRatio",
			"weeklyMood",
			"bestCurrentStreak"
		}
)
public record DashboardResponse(
		@Schema(nullable = true) DailyEntryResponse todayEntry,
		List<HabitResponse> activeHabits,
		int completedHabitCount,
		int totalHabitCount,
		double completionRatio,
		WeeklyMoodSummary weeklyMood,
		int bestCurrentStreak
) {
	public DashboardResponse {
		activeHabits = List.copyOf(activeHabits);
	}

	public static DashboardResponse from(DashboardView view) {
		return new DashboardResponse(
				view.todayEntry() == null
						? null
						: DailyEntryResponse.from(view.todayEntry()),
				view.activeHabits().stream().map(HabitResponse::from).toList(),
				view.completedHabitCount(),
				view.totalHabitCount(),
				view.completionRatio(),
				WeeklyMoodSummary.from(view.weeklyMood()),
				view.bestCurrentStreak()
		);
	}

	@Schema(requiredProperties = {"entryCount"})
	public record WeeklyMoodSummary(
			@Schema(nullable = true) Double averageScore,
			long entryCount
	) {
		private static WeeklyMoodSummary from(WeeklyMoodView view) {
			return new WeeklyMoodSummary(view.averageScore(), view.entryCount());
		}
	}
}
