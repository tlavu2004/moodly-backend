package com.tlavu.moodly.modules.entries.service.view;

import com.tlavu.moodly.modules.entries.model.DailyEntry;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record DailyEntryView(
		String id,
		String userId,
		LocalDate date,
		MoodView mood,
		List<HabitLogView> habits,
		Instant createdAt,
		Instant updatedAt
) {
	public DailyEntryView {
		habits = List.copyOf(habits);
	}

	public static DailyEntryView from(DailyEntry entry) {
		return new DailyEntryView(
				entry.getId(),
				entry.getUserId(),
				entry.getDate(),
				entry.getMood() == null
						? null
						: MoodView.from(entry.getMood()),
				entry.getHabits().stream()
						.map(HabitLogView::from)
						.toList(),
				entry.getCreatedAt(),
				entry.getUpdatedAt()
		);
	}
}
