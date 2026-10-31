package com.tlavu.moodly.modules.search.model.elasticsearch;

import com.tlavu.moodly.modules.entries.model.DailyEntry;
import java.time.LocalDate;
import java.util.List;

/** Denormalized projection stored in Elasticsearch; MongoDB remains authoritative. */
public record DailyEntrySearchDocument(
		String userId,
		LocalDate date,
		IndexedMood mood,
		List<IndexedHabitLog> habits
) {
	public static DailyEntrySearchDocument from(DailyEntry entry) {
		com.tlavu.moodly.modules.entries.model.Mood mood = entry.getMood();
		IndexedMood searchMood = mood == null ? null : new IndexedMood(mood.getScore(), mood.getNote(), mood.getTags());
		List<IndexedHabitLog> habits = entry.getHabits() == null
				? List.<IndexedHabitLog>of()
				: entry.getHabits().stream().map(habit -> new IndexedHabitLog(habit.getNote())).toList();
		return new DailyEntrySearchDocument(entry.getUserId(), entry.getDate(), searchMood, habits);
	}
}
