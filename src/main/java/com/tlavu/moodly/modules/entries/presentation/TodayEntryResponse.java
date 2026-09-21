package com.tlavu.moodly.modules.entries.presentation;

import com.tlavu.moodly.modules.entries.domain.DailyEntry;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

public record TodayEntryResponse(
		LocalDate date,
		boolean checkedIn,
		@Schema(nullable = true, description = "The complete entry, or null when the user has not checked in today.") DailyEntry entry
) {
	public static TodayEntryResponse empty(LocalDate date) {
		return new TodayEntryResponse(date, false, null);
	}
}
