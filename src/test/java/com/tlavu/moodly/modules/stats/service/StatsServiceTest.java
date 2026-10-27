package com.tlavu.moodly.modules.stats.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tlavu.moodly.modules.entries.service.implementation.EntryReadService;
import com.tlavu.moodly.modules.entries.model.DailyEntry;
import com.tlavu.moodly.modules.entries.model.HabitLog;
import com.tlavu.moodly.modules.entries.service.view.DailyEntryView;
import com.tlavu.moodly.modules.stats.domain.MoodTrendPeriod;
import com.tlavu.moodly.modules.stats.dto.response.MoodTrendResponse;
import com.tlavu.moodly.modules.stats.dto.response.MostMissedHabitResponse;
import com.tlavu.moodly.modules.stats.repository.StatsAggregationRepository;
import java.time.LocalDate;
import java.util.List;
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
	private StatsAggregationRepository statsAggregationRepository;
	@InjectMocks
	private StatsService statsService;

	@Test
	void delegatesAggregateQueries() {
		var trend = List.of(new MoodTrendResponse(LocalDate.of(2026, 8, 3), 4.0, 2));
		var missed = List.of(new MostMissedHabitResponse("reading", 3));
		when(statsAggregationRepository.findMoodTrend(
				"user-1",
				LocalDate.of(2026, 8, 3),
				LocalDate.of(2026, 8, 9)
		)).thenReturn(trend);
		when(statsAggregationRepository.findMostMissedHabits("user-1")).thenReturn(missed);

		assertEquals(trend, statsService.findMoodTrend(
				"user-1",
				MoodTrendPeriod.WEEK,
				LocalDate.of(2026, 8, 5)
		));
		assertEquals(missed, statsService.findMostMissedHabits("user-1"));
	}

	@Test
	void calculatesStreakUntilMissingDayOrIncompleteHabit() {
		var today = LocalDate.of(2026, 8, 5);
		when(entryReadService.findOnOrBefore("user-1", today)).thenReturn(List.of(
				entry(today, true),
				entry(today.minusDays(1), true),
				entry(today.minusDays(2), false)
		));

		assertEquals(2, statsService.calculateCurrentStreak("user-1", "exercise", today).currentStreak());
	}

	@Test
	void stopsStreakWhenDateIsMissing() {
		var today = LocalDate.of(2026, 8, 5);
		when(entryReadService.findOnOrBefore("user-1", today)).thenReturn(List.of(
				entry(today, true),
				entry(today.minusDays(2), true)
		));

		assertEquals(1, statsService.calculateCurrentStreak("user-1", "exercise", today).currentStreak());
		verify(entryReadService).findOnOrBefore("user-1", today);
	}

	@Test
	void returnsZeroStreakWhenNoEntriesExist() {
		var today = LocalDate.of(2026, 8, 5);
		when(entryReadService.findOnOrBefore("user-1", today)).thenReturn(List.of());

		assertEquals(0, statsService.calculateCurrentStreak("user-1", "exercise", today).currentStreak());
	}

	@Test
	void returnsZeroStreakWhenTodayDoesNotContainTheHabitLog() {
		var today = LocalDate.of(2026, 8, 5);
		var entry = new DailyEntry("user-1", today);
		entry.getHabits().add(new HabitLog("reading", true, null));
		when(entryReadService.findOnOrBefore("user-1", today)).thenReturn(List.of(DailyEntryView.from(entry)));

		assertEquals(0, statsService.calculateCurrentStreak("user-1", "exercise", today).currentStreak());
	}

	@Test
	void calculatesTheBestStreakFromOneEntryHistoryRead() {
		var today = LocalDate.of(2026, 8, 5);
		var todayEntry = new DailyEntry("user-1", today);
		todayEntry.getHabits().addAll(List.of(
				new HabitLog("exercise", true, null),
				new HabitLog("reading", true, null)
		));
		var yesterdayEntry = new DailyEntry("user-1", today.minusDays(1));
		yesterdayEntry.getHabits().addAll(List.of(
				new HabitLog("exercise", false, null),
				new HabitLog("reading", true, null)
		));
		when(entryReadService.findOnOrBefore("user-1", today))
				.thenReturn(List.of(DailyEntryView.from(todayEntry), DailyEntryView.from(yesterdayEntry)));

		assertEquals(2, statsService.calculateBestCurrentStreak(
				"user-1",
				List.of("exercise", "reading"),
				today
		));
		verify(entryReadService).findOnOrBefore("user-1", today);
	}

	private DailyEntryView entry(LocalDate date, boolean done) {
		var entry = new DailyEntry("user-1", date);
		entry.getHabits().add(new HabitLog("exercise", done, null));
		return DailyEntryView.from(entry);
	}
}
