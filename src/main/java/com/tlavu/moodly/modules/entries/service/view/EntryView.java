package com.tlavu.moodly.modules.entries.service.view;

import com.tlavu.moodly.modules.entries.model.DailyEntry;
import com.tlavu.moodly.modules.entries.model.Mood;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record EntryView(
		String id,
		String userId,
		LocalDate date,
		MoodView mood,
		List<HabitLogView> habits,
		Instant createdAt,
		Instant updatedAt
) {

	public EntryView {
		habits = List.copyOf(habits);
	}

	public static EntryView from(DailyEntry entry) {
		return new EntryView(
				entry.getId(),
				entry.getUserId(),
				entry.getDate(),
				entry.getMood() == null ? null : MoodView.from(entry.getMood()),
				entry.getHabits().stream().map(HabitLogView::from).toList(),
				entry.getCreatedAt(),
				entry.getUpdatedAt()
		);
	}

	public record MoodView(int score, List<String> tags, String note) {

		public MoodView {
			tags = List.copyOf(tags);
		}

		private static MoodView from(Mood mood) {
			return new MoodView(mood.getScore(), mood.getTags(), mood.getNote());
		}
	}

	public record HabitLogView(String habitId, boolean done, String note) {

		private static HabitLogView from(DailyEntry.HabitLog log) {
			return new HabitLogView(log.getHabitId(), log.isDone(), log.getNote());
		}
	}
}
