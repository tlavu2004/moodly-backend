package com.tlavu.moodly.modules.stats.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import com.tlavu.moodly.shared.time.MoodlyTime;
import java.time.temporal.TemporalAdjusters;

public enum MoodTrendPeriod {

	WEEK("week");

	public static final ZoneId TIME_ZONE = MoodlyTime.ZONE;

	private final String value;

	MoodTrendPeriod(String value) {
		this.value = value;
	}

	@JsonValue
	public String value() {
		return value;
	}

	@JsonCreator
	public static MoodTrendPeriod fromValue(String value) {
		for (var period : values()) {
			if (period.value.equals(value)) {
				return period;
			}
		}
		throw new IllegalArgumentException("Unsupported mood trend period: " + value + ". Supported values: week.");
	}

	public LocalDate startDate(LocalDate date) {
		return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
	}

	public LocalDate endDate(LocalDate date) {
		return startDate(date).plusDays(6);
	}

}
