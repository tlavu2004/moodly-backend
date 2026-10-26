package com.tlavu.moodly.modules.entries.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class Mood {

	private int score;
	private List<String> tags = new ArrayList<>();
	private String note;

	public Mood(int score, List<String> tags, String note) {
		this.score = score;
		this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
		this.note = note;
	}

	public List<String> getTags() {
		return List.copyOf(tags);
	}
}
