package com.tlavu.moodly.modules.entries.dto.response;

import com.tlavu.moodly.modules.entries.service.view.DailyEntryView;
import com.tlavu.moodly.modules.entries.service.view.HabitLogView;
import com.tlavu.moodly.modules.entries.service.view.MoodView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record DailyEntryResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String userId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate date,
		@Schema(nullable = true) MoodResponse mood,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<HabitLogResponse> habits,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt
) {

	public DailyEntryResponse {
		habits = List.copyOf(habits);
	}

	public static DailyEntryResponse from(DailyEntryView entry) {
		return new DailyEntryResponse(
				entry.id(),
				entry.userId(),
				entry.date(),
				entry.mood() == null ? null : MoodResponse.from(entry.mood()),
				entry.habits().stream().map(HabitLogResponse::from).toList(),
				entry.createdAt(),
				entry.updatedAt()
		);
	}

	public record MoodResponse(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int score,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> tags,
			@Schema(nullable = true) String note
	) {

		public MoodResponse {
			tags = List.copyOf(tags);
		}

		private static MoodResponse from(MoodView mood) {
			return new MoodResponse(mood.score(), mood.tags(), mood.note());
		}
	}

	public record HabitLogResponse(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String habitId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean done,
			@Schema(nullable = true) String note
	) {

		private static HabitLogResponse from(HabitLogView log) {
			return new HabitLogResponse(log.habitId(), log.done(), log.note());
		}
	}
}
