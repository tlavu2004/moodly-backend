package com.tlavu.moodly.modules.cdc.application;

import com.tlavu.moodly.modules.entries.domain.DailyEntry;
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
		var mood = entry.getMood();
		var searchMood = mood == null ? null : new Mood(mood.getScore(), mood.getNote(), mood.getTags());
		var habits = entry.getHabits() == null
				? List.<Habit>of()
				: entry.getHabits().stream().map(habit -> new Habit(habit.getNote())).toList();
		return new DailyEntrySearchDocument(entry.getUserId(), entry.getDate(), searchMood, habits);
	}
}
