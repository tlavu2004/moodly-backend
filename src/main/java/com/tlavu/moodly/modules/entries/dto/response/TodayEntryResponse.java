package com.tlavu.moodly.modules.entries.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

public record TodayEntryResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate date,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean checkedIn,
		@Schema(
				nullable = true,
				description = "The complete entry, or null when the user has not checked in today."
		) DailyEntryResponse entry
) {
	public static TodayEntryResponse empty(LocalDate date) {
		return new TodayEntryResponse(
				date,
				false,
				null
		);
	}
}
