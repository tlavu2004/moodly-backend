package com.tlavu.moodly.modules.stats.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tlavu.moodly.modules.entries.domain.DailyEntry;
import com.tlavu.moodly.modules.entries.infrastructure.DailyEntryRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class StatsServiceIntegrationTest {

	private static final String TEST_USER_ID = "__test_stats_user__";

	@Autowired
	private DailyEntryRepository dailyEntryRepository;

	@Autowired
	private StatsService statsService;

	@BeforeEach
	void seedEntries() {
		dailyEntryRepository.save(entry(
				LocalDate.of(2026, 8, 1),
				3,
				new DailyEntry.HabitLog("exercise", true, null),
				new DailyEntry.HabitLog("reading", false, null)
		));
		dailyEntryRepository.save(entry(
				LocalDate.of(2026, 8, 2),
				2,
				new DailyEntry.HabitLog("exercise", true, null),
				new DailyEntry.HabitLog("reading", false, null)
		));
		dailyEntryRepository.save(entry(
				LocalDate.of(2026, 8, 3),
				4,
				new DailyEntry.HabitLog("exercise", false, null)
		));
	}

	@AfterEach
	void cleanUp() {
		dailyEntryRepository.deleteByUserId(TEST_USER_ID);
	}

	@Test
	void groupsMoodByWeekAndFindsMostMissedHabit() {
		var trend = statsService.findWeeklyMoodTrend(TEST_USER_ID);
		var missedHabits = statsService.findMostMissedHabits(TEST_USER_ID);

		assertEquals(2, trend.size());
		assertEquals("reading", missedHabits.getFirst().habitId());
		assertEquals(2, missedHabits.getFirst().missedCount());
	}

	@Test
	void calculatesOnlyConsecutiveCompletedDays() {
		var streak = statsService.calculateCurrentStreak(
				TEST_USER_ID,
				"exercise",
				LocalDate.of(2026, 8, 2)
		);

		assertEquals(2, streak.currentStreak());
	}

	private DailyEntry entry(LocalDate date, int moodScore, DailyEntry.HabitLog... habitLogs) {
		var entry = new DailyEntry(TEST_USER_ID, date);
		entry.setMood(new DailyEntry.Mood(moodScore, List.of("test"), null));
		entry.getHabits().addAll(List.of(habitLogs));
		return entry;
	}
}
