package com.tlavu.moodly.modules.stats.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tlavu.moodly.modules.entries.model.DailyEntry;
import com.tlavu.moodly.modules.entries.model.HabitLog;
import com.tlavu.moodly.modules.entries.model.Mood;
import com.tlavu.moodly.modules.entries.repository.DailyEntryRepository;
import com.tlavu.moodly.modules.stats.domain.MoodTrendPeriod;
import com.tlavu.moodly.modules.stats.service.view.HabitStreakView;
import com.tlavu.moodly.modules.stats.service.view.MoodTrendView;
import com.tlavu.moodly.modules.stats.service.view.MostMissedHabitView;
import com.tlavu.moodly.support.MongoTestConfiguration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(MongoTestConfiguration.class)
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
				new HabitLog("exercise", true, null),
				new HabitLog("reading", false, null)
		));
		dailyEntryRepository.save(entry(
				LocalDate.of(2026, 8, 2),
				2,
				new HabitLog("exercise", true, null),
				new HabitLog("reading", false, null)
		));
		dailyEntryRepository.save(entry(
				LocalDate.of(2026, 8, 3),
				4,
				new HabitLog("exercise", false, null)
		));
	}

	@AfterEach
	void cleanUp() {
		dailyEntryRepository.deleteByUserId(TEST_USER_ID);
	}

	@Test
	void groupsMoodByWeekAndFindsMostMissedHabit() {
		List<MoodTrendView> trend = statsService.findMoodTrend(
				TEST_USER_ID,
				MoodTrendPeriod.WEEK,
				LocalDate.of(2026, 8, 3)
		);
		List<MostMissedHabitView> missedHabits = statsService.findMostMissedHabits(TEST_USER_ID);

		assertEquals(1, trend.size());
		assertEquals(LocalDate.of(2026, 8, 3), trend.getFirst().date());
		assertEquals("reading", missedHabits.getFirst().habitId());
		assertEquals(2, missedHabits.getFirst().missedCount());
	}

	@Test
	void calculatesOnlyConsecutiveCompletedDays() {
		HabitStreakView streak = statsService.calculateCurrentStreak(
				TEST_USER_ID,
				"exercise",
				LocalDate.of(2026, 8, 2)
		);

		assertEquals(2, streak.currentStreak());
	}

	private DailyEntry entry(LocalDate date, int moodScore, HabitLog... habitLogs) {
		DailyEntry entry = new DailyEntry(TEST_USER_ID, date);
		entry.setMood(new Mood(moodScore, List.of("test"), null));
		entry.getHabits().addAll(List.of(habitLogs));
		return entry;
	}
}
