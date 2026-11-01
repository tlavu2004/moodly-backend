package com.tlavu.moodly.modules.entries.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "#{@environment.getProperty('moodly.entries.collection-name')}")
@CompoundIndexes({
		@CompoundIndex(
				name = "user_date_unique",
				def = "{ 'userId': 1, 'date': 1 }",
				unique = true
		),
		@CompoundIndex(
				name = "user_habit_done_idx",
				def = "{ 'userId': 1, 'habits.done': 1, 'habits.habitId': 1 }"
		)
})
@Getter
@Setter
@NoArgsConstructor
public class DailyEntry {
	@Id
	private String id;
	private String userId;
	private LocalDate date;
	private Mood mood;
	private List<HabitLog> habits = new ArrayList<>();
	private Instant createdAt;
	private Instant updatedAt;

	public DailyEntry(String userId, LocalDate date) {
		this.userId = userId;
		this.date = date;
	}
}
