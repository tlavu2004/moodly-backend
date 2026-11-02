package com.tlavu.moodly.modules.stats.service.implementation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tlavu.moodly.modules.entries.service.implementation.EntryReadService;
import com.tlavu.moodly.modules.entries.model.DailyEntry;
import com.tlavu.moodly.modules.entries.model.HabitLog;
import com.tlavu.moodly.modules.entries.service.view.DailyEntryView;
import com.tlavu.moodly.modules.habits.model.Habit;
import com.tlavu.moodly.modules.habits.domain.TargetFrequency;
import com.tlavu.moodly.modules.habits.repository.HabitRepository;
import com.tlavu.moodly.modules.stats.domain.MoodTrendPeriod;
import com.tlavu.moodly.modules.stats.service.implementation.StatsService;
import com.tlavu.moodly.modules.stats.service.input.CalculateBestCurrentStreakInput;
import com.tlavu.moodly.modules.stats.service.input.CalculateCurrentStreakInput;
import com.tlavu.moodly.modules.stats.service.input.FindMoodTrendInput;
import com.tlavu.moodly.modules.stats.service.view.MoodTrendView;
import com.tlavu.moodly.modules.stats.service.view.MostMissedHabitView;
import com.tlavu.moodly.modules.stats.repository.contract.StatsAggregationRepository;
import com.tlavu.moodly.modules.stats.repository.projection.MoodTrendProjection;
import com.tlavu.moodly.modules.stats.repository.projection.MostMissedHabitProjection;
import com.tlavu.moodly.shared.error.exception.common.ResourceNotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

	@Mock
	private EntryReadService entryReadService;
	@Mock
	private HabitRepository habitRepository;
	@Mock
	private StatsAggregationRepository statsAggregationRepository;
	@InjectMocks
	private StatsService statsService;

	@Test
	void delegatesAggregateQueries() {
		List<MoodTrendView> trend = List.of(new MoodTrendView(LocalDate.of(2026, 8, 3), 4.0, 2));
		List<MostMissedHabitView> missed = List.of(new MostMissedHabitView("reading", 3));
		when(statsAggregationRepository.findMoodTrend(
				"user-1",
				LocalDate.of(2026, 8, 3),
				LocalDate.of(2026, 8, 9)
		)).thenReturn(List.of(new MoodTrendProjection(LocalDate.of(2026, 8, 3), 4.0, 2)));
		when(statsAggregationRepository.findMostMissedHabits("user-1"))
				.thenReturn(List.of(new MostMissedHabitProjection("reading", 3)));

		assertEquals(trend, statsService.findMoodTrend(
				"user-1",
				new FindMoodTrendInput(MoodTrendPeriod.WEEK, LocalDate.of(2026, 8, 5))
		));
		assertEquals(missed, statsService.findMostMissedHabits("user-1"));
	}

	@Test
	void calculatesStreakUntilMissingDayOrIncompleteHabit() {
		LocalDate today = LocalDate.of(2026, 8, 5);
		ownedExercise();
		when(entryReadService.findOnOrBefore("user-1", today)).thenReturn(List.of(
				entry(today, true),
				entry(today.minusDays(1), true),
				entry(today.minusDays(2), false)
		));

		assertEquals(2, statsService.calculateCurrentStreak(
				"user-1",
				new CalculateCurrentStreakInput("exercise", today)
		).currentStreak());
	}

	@Test
	void stopsStreakWhenDateIsMissing() {
		LocalDate today = LocalDate.of(2026, 8, 5);
		ownedExercise();
		when(entryReadService.findOnOrBefore("user-1", today)).thenReturn(List.of(
				entry(today, true),
				entry(today.minusDays(2), true)
		));

		assertEquals(1, statsService.calculateCurrentStreak(
				"user-1",
				new CalculateCurrentStreakInput("exercise", today)
		).currentStreak());
		verify(entryReadService).findOnOrBefore("user-1", today);
	}

	@Test
	void returnsZeroStreakWhenNoEntriesExist() {
		LocalDate today = LocalDate.of(2026, 8, 5);
		ownedExercise();
		when(entryReadService.findOnOrBefore("user-1", today)).thenReturn(List.of());

		assertEquals(0, statsService.calculateCurrentStreak(
				"user-1",
				new CalculateCurrentStreakInput("exercise", today)
		).currentStreak());
	}

	@Test
	void returnsZeroStreakWhenTodayDoesNotContainTheHabitLog() {
		LocalDate today = LocalDate.of(2026, 8, 5);
		ownedExercise();
		DailyEntry entry = new DailyEntry("user-1", today);
		entry.getHabits().add(new HabitLog("reading", true, null));
		when(entryReadService.findOnOrBefore("user-1", today)).thenReturn(List.of(DailyEntryView.from(entry)));

		assertEquals(0, statsService.calculateCurrentStreak(
				"user-1",
				new CalculateCurrentStreakInput("exercise", today)
		).currentStreak());
	}

	@Test
	void rejectsMissingOrAnotherUsersHabitBeforeReadingEntries() {
		LocalDate today = LocalDate.of(2026, 8, 5);
		when(habitRepository.findByIdAndUserId("exercise", "user-1")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> statsService.calculateCurrentStreak("user-1",
				new CalculateCurrentStreakInput("exercise", today)))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Habit was not found.");
		verifyNoInteractions(entryReadService);
	}

	private void ownedExercise() {
		when(habitRepository.findByIdAndUserId("exercise", "user-1"))
				.thenReturn(Optional.of(new Habit("exercise", "user-1", "Exercise", "walk", TargetFrequency.DAILY, true)));
	}

	@Test
	void calculatesTheBestStreakFromOneEntryHistoryRead() {
		LocalDate today = LocalDate.of(2026, 8, 5);
		DailyEntry todayEntry = new DailyEntry("user-1", today);
		todayEntry.getHabits().addAll(List.of(
				new HabitLog("exercise", true, null),
				new HabitLog("reading", true, null)
		));
		DailyEntry yesterdayEntry = new DailyEntry("user-1", today.minusDays(1));
		yesterdayEntry.getHabits().addAll(List.of(
				new HabitLog("exercise", false, null),
				new HabitLog("reading", true, null)
		));
		when(entryReadService.findOnOrBefore("user-1", today))
				.thenReturn(List.of(DailyEntryView.from(todayEntry), DailyEntryView.from(yesterdayEntry)));

		assertEquals(2, statsService.calculateBestCurrentStreak(
				"user-1",
				new CalculateBestCurrentStreakInput(List.of("exercise", "reading"), today)
		));
		verify(entryReadService).findOnOrBefore("user-1", today);
	}

	private DailyEntryView entry(LocalDate date, boolean done) {
		DailyEntry entry = new DailyEntry("user-1", date);
		entry.getHabits().add(new HabitLog("exercise", done, null));
		return DailyEntryView.from(entry);
	}
}
