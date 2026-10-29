package com.tlavu.moodly.modules.stats.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import com.tlavu.moodly.shared.error.exception.common.InvalidRequestException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public enum MoodTrendPeriod {
	WEEK("week");

	private final String value;

	MoodTrendPeriod(String value) {
		this.value = value;
	}

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
		throw new InvalidRequestException("Unsupported mood trend period. Supported values: week.");
	}

	public LocalDate startDate(LocalDate date) {
		return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
	}

	public LocalDate endDate(LocalDate date) {
		return startDate(date).plusDays(6);
	}
}
