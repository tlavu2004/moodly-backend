package com.tlavu.moodly.modules.cdc.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.tlavu.moodly.modules.entries.domain.DailyEntry;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DailyEntrySearchDocumentTest {

	@Test
	void denormalizesSearchableMoodAndHabitContent() {
		var entry = new DailyEntry("user-1", LocalDate.of(2026, 8, 6));
		entry.setId("entry-1");
		entry.setMood(new DailyEntry.Mood(4, List.of("calm", "focused"), "A productive day"));
		entry.setHabits(List.of(
				new DailyEntry.HabitLog("exercise", true, "Morning run"),
				new DailyEntry.HabitLog("reading", false, "Too tired")
		));

		var document = DailyEntrySearchDocument.from(entry);

		assertThat(document.userId()).isEqualTo("user-1");
		assertThat(document.date()).isEqualTo(LocalDate.of(2026, 8, 6));
		assertThat(document.mood()).isEqualTo(new DailyEntrySearchDocument.Mood(4, "A productive day", List.of("calm", "focused")));
		assertThat(document.habits()).containsExactly(
				new DailyEntrySearchDocument.Habit("Morning run"),
				new DailyEntrySearchDocument.Habit("Too tired")
		);
	}

	@Test
	void handlesAnEntryWithoutMood() {
		var entry = new DailyEntry("user-1", LocalDate.of(2026, 8, 6));

		var document = DailyEntrySearchDocument.from(entry);

		assertThat(document.mood()).isNull();
		assertThat(document.habits()).isEmpty();
	}

	@Test
	void handlesEmptyTagsAndNullHabitCollection() {
		var entry = new DailyEntry("user-1", LocalDate.of(2026, 8, 6));
		entry.setMood(new DailyEntry.Mood(3, null, null));
		entry.setHabits(null);

		var document = DailyEntrySearchDocument.from(entry);

		assertThat(document.mood().tags()).isEmpty();
		assertThat(document.habits()).isEmpty();
	}
}
