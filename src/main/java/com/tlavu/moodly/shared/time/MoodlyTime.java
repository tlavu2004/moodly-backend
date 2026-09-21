package com.tlavu.moodly.shared.time;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

public final class MoodlyTime {
	public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

	private MoodlyTime() {}

	public static LocalDate today(Clock clock) {
		return LocalDate.now(clock.withZone(ZONE));
	}
}
