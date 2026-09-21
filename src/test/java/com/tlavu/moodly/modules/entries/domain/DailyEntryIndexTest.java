package com.tlavu.moodly.modules.entries.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

class DailyEntryIndexTest {
	@Test
	void declaresIndexesForDateAndMissedHabitQueries() {
		var indexes = DailyEntry.class.getAnnotation(CompoundIndexes.class);
		assertThat(indexes).isNotNull();
		assertThat(Arrays.stream(indexes.value()).map(index -> index.name()))
				.contains("user_date_unique", "user_habit_done_idx");
	}
}
