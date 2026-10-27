package com.tlavu.moodly.modules.entries.dto.response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tlavu.moodly.modules.entries.model.DailyEntry;
import com.tlavu.moodly.modules.entries.model.HabitLog;
import com.tlavu.moodly.modules.entries.model.Mood;
import com.tlavu.moodly.modules.entries.service.view.DailyEntryView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DailyEntryResponseTest {

	@Test
	void copiesEntryDataWithoutSharingMutableModelState() {
		DailyEntry entry = new DailyEntry("user-1", LocalDate.of(2026, 8, 5));
		entry.setId("entry-1");
		entry.setCreatedAt(Instant.parse("2026-08-05T08:00:00Z"));
		entry.setUpdatedAt(Instant.parse("2026-08-05T09:00:00Z"));
		entry.setMood(new Mood(4, List.of("calm"), "Good day"));
		entry.getHabits().add(new HabitLog("habit-1", true, "Done"));

		DailyEntryResponse response = DailyEntryResponse.from(DailyEntryView.from(entry));
		entry.getHabits().getFirst().setNote("Changed later");
		entry.getHabits().add(new HabitLog("habit-2", false, null));

		assertEquals("entry-1", response.id());
		assertEquals("user-1", response.userId());
		assertEquals(Instant.parse("2026-08-05T08:00:00Z"), response.createdAt());
		assertEquals(Instant.parse("2026-08-05T09:00:00Z"), response.updatedAt());
		assertEquals("Done", response.habits().getFirst().note());
		assertEquals(1, response.habits().size());
		assertEquals(List.of("calm"), response.mood().tags());
		assertThrows(UnsupportedOperationException.class, () -> response.habits().clear());
		assertThrows(UnsupportedOperationException.class, () -> response.mood().tags().clear());
	}
}
