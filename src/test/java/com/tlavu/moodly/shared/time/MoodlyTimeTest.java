package com.tlavu.moodly.shared.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class MoodlyTimeTest {

	@Test
	void changesTheBusinessDateAtHoChiMinhMidnight() {
		var beforeMidnight = Clock.fixed(Instant.parse("2026-09-21T16:59:59Z"), ZoneOffset.UTC);
		var atMidnight = Clock.fixed(Instant.parse("2026-09-21T17:00:00Z"), ZoneOffset.UTC);

		assertThat(MoodlyTime.today(beforeMidnight)).isEqualTo(LocalDate.of(2026, 9, 21));
		assertThat(MoodlyTime.today(atMidnight)).isEqualTo(LocalDate.of(2026, 9, 22));
	}

	@Test
	void usesAZoneWithoutDaylightSavingTransitions() {
		assertThat(MoodlyTime.ZONE.getRules().nextTransition(Instant.parse("2026-01-01T00:00:00Z"))).isNull();
	}
}
