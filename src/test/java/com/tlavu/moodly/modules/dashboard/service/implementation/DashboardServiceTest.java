package com.tlavu.moodly.modules.dashboard.service.implementation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import com.tlavu.moodly.modules.dashboard.service.implementation.DashboardService;
import com.tlavu.moodly.modules.entries.model.DailyEntry;
import com.tlavu.moodly.modules.entries.model.HabitLog;
import com.tlavu.moodly.modules.entries.service.implementation.EntryReadService;
import com.tlavu.moodly.modules.entries.service.view.DailyEntryView;
import com.tlavu.moodly.modules.habits.domain.TargetFrequency;
import com.tlavu.moodly.modules.habits.model.Habit;
import com.tlavu.moodly.modules.habits.service.implementation.HabitService;
import com.tlavu.moodly.modules.habits.service.view.HabitView;
import com.tlavu.moodly.modules.stats.domain.MoodTrendPeriod;
import com.tlavu.moodly.modules.stats.service.view.MoodTrendView;
import com.tlavu.moodly.modules.stats.service.implementation.StatsService;
import com.tlavu.moodly.modules.stats.service.input.CalculateBestCurrentStreakInput;
import com.tlavu.moodly.modules.stats.service.input.FindMoodTrendInput;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

	@Mock
	private EntryReadService entryReadService;
	@Mock
	private HabitService habitService;
	@Mock
	private StatsService statsService;
	@InjectMocks
	private DashboardService dashboardService;

	@Test
	void buildsTheAuthenticatedUsersDashboardSummary() {
		var today = LocalDate.of(2026, 8, 5);
		var habits = List.of(
				HabitView.from(new Habit("exercise", "user-1", "Exercise", null, TargetFrequency.DAILY, true)),
				HabitView.from(new Habit("reading", "user-1", "Read", null, TargetFrequency.DAILY, true))
		);
		var entry = new DailyEntry("user-1", today);
		entry.getHabits().addAll(List.of(
				new HabitLog("exercise", true, null),
				new HabitLog("reading", false, null),
				new HabitLog("archived", true, null)
		));
		when(habitService.findActive("user-1")).thenReturn(habits);
		when(entryReadService.findByDate("user-1", today)).thenReturn(Optional.of(DailyEntryView.from(entry)));
		when(statsService.findMoodTrend(
				"user-1",
				new FindMoodTrendInput(MoodTrendPeriod.WEEK, today)
		)).thenReturn(List.of(
				new MoodTrendView(today.minusDays(1), 3.0, 1),
				new MoodTrendView(today, 5.0, 3)
		));
		when(statsService.calculateBestCurrentStreak(
				"user-1",
				new CalculateBestCurrentStreakInput(List.of("exercise", "reading"), today)
		))
				.thenReturn(4);

		var result = dashboardService.getDashboard("user-1", today);

		assertEquals(DailyEntryView.from(entry), result.todayEntry());
		assertEquals(habits, result.activeHabits());
		assertEquals(1, result.completedHabitCount());
		assertEquals(2, result.totalHabitCount());
		assertEquals(0.5, result.completionRatio());
		assertEquals(4.5, result.weeklyMood().averageScore());
		assertEquals(4, result.weeklyMood().entryCount());
		assertEquals(4, result.bestCurrentStreak());
	}

	@Test
	void returnsAnEmptySummaryWhenTheUserHasNoDashboardData() {
		var today = LocalDate.of(2026, 8, 5);
		when(habitService.findActive("user-1")).thenReturn(List.of());
		when(entryReadService.findByDate("user-1", today)).thenReturn(Optional.empty());
		when(statsService.findMoodTrend(
				"user-1",
				new FindMoodTrendInput(MoodTrendPeriod.WEEK, today)
		)).thenReturn(List.of());
		when(statsService.calculateBestCurrentStreak(
				"user-1",
				new CalculateBestCurrentStreakInput(List.of(), today)
		)).thenReturn(0);

		var result = dashboardService.getDashboard("user-1", today);

		assertNull(result.todayEntry());
		assertEquals(List.of(), result.activeHabits());
		assertEquals(0, result.completedHabitCount());
		assertEquals(0, result.totalHabitCount());
		assertEquals(0.0, result.completionRatio());
		assertNull(result.weeklyMood().averageScore());
		assertEquals(0, result.weeklyMood().entryCount());
		assertEquals(0, result.bestCurrentStreak());
	}

}
