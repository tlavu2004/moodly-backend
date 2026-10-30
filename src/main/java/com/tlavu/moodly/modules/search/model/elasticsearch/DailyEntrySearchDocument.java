package com.tlavu.moodly.modules.search.model.elasticsearch;

import com.tlavu.moodly.modules.entries.model.DailyEntry;
import java.time.LocalDate;
import java.util.List;

/** Denormalized projection stored in Elasticsearch; MongoDB remains authoritative. */
public record DailyEntrySearchDocument(
		String userId,
		LocalDate date,
		Mood mood,
		List<Habit> habits
) {

	public record Mood(Integer score, String note, List<String> tags) {
	}

	public record Habit(String note) {
	}

	public static DailyEntrySearchDocument from(DailyEntry entry) {
		com.tlavu.moodly.modules.entries.model.Mood mood = entry.getMood();
		Mood searchMood = mood == null ? null : new Mood(mood.getScore(), mood.getNote(), mood.getTags());
		List<Habit> habits = entry.getHabits() == null
				? List.<Habit>of()
				: entry.getHabits().stream().map(habit -> new Habit(habit.getNote())).toList();
		return new DailyEntrySearchDocument(entry.getUserId(), entry.getDate(), searchMood, habits);
	}
}
