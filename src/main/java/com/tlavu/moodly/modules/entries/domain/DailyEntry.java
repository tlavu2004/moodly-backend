package com.tlavu.moodly.modules.entries.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import io.swagger.v3.oas.annotations.media.Schema;

@Document(collection = "#{@environment.getProperty('moodly.entries.collection-name')}")
@CompoundIndex(name = "user_date_unique", def = "{ 'userId': 1, 'date': 1 }", unique = true)
@Getter
@Setter
public class DailyEntry {

	@Id
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	private String id;
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	private String userId;
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	private LocalDate date;
	@Schema(nullable = true)
	private Mood mood;
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	private List<HabitLog> habits = new ArrayList<>();
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	private Instant createdAt;
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	private Instant updatedAt;

	public DailyEntry() {
	}

	public DailyEntry(String userId, LocalDate date) {
		this.userId = userId;
		this.date = date;
	}

	@Getter
	public static class Mood {

		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) private int score;
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
		private List<String> tags = new ArrayList<>();
		@Schema(nullable = true)
		private String note;

		public Mood() {
		}

		public Mood(int score, List<String> tags, String note) {
			this.score = score;
			this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
			this.note = note;
		}

		public List<String> getTags() {
			return List.copyOf(tags);
		}
	}

	@Getter
	@Setter
	public static class HabitLog {

		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) private String habitId;
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) private boolean done;
		@Schema(nullable = true)
		private String note;

		public HabitLog() {
		}

		public HabitLog(String habitId, boolean done, String note) {
			this.habitId = habitId;
			this.done = done;
			this.note = note;
		}

	}
}
