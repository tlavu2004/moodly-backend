package com.tlavu.moodly.modules.stats.domain;

import lombok.RequiredArgsConstructor;

import com.fasterxml.jackson.annotation.JsonValue;
import com.tlavu.moodly.modules.stats.exception.StatsException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

@RequiredArgsConstructor
public enum MoodTrendPeriod {
	WEEK("week");

	private final String value;

	@JsonValue
	public String value() {
		return value;
	}

	public static MoodTrendPeriod fromValue(String value) {
		for (MoodTrendPeriod period : values()) {
			if (period.value.equals(value)) {
				return period;
			}
		}
		throw StatsException.invalidPeriod();
	}

	public LocalDate startDate(LocalDate date) {
		return date.with(
			TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
		);
	}

	public LocalDate endDate(LocalDate date) {
		return startDate(date).plusDays(6);
	}
}
