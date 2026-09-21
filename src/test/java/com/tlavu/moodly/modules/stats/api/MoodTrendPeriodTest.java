package com.tlavu.moodly.modules.stats.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MoodTrendPeriodTest {

	@Test
	void definesTheCurrentMondayThroughSundayWeek() {
		var date = LocalDate.of(2026, 8, 5);

		assertEquals(LocalDate.of(2026, 8, 3), MoodTrendPeriod.WEEK.startDate(date));
		assertEquals(LocalDate.of(2026, 8, 9), MoodTrendPeriod.WEEK.endDate(date));
	}

	@Test
	void acceptsOnlyTheDocumentedApiValue() {
		assertEquals(MoodTrendPeriod.WEEK, MoodTrendPeriod.fromValue("week"));
		assertThrows(IllegalArgumentException.class, () -> MoodTrendPeriod.fromValue("month"));
	}

}
