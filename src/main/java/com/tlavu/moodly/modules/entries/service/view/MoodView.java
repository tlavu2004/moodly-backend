package com.tlavu.moodly.modules.entries.service.view;

import com.tlavu.moodly.modules.entries.model.Mood;
import java.util.List;

public record MoodView(
		int score,
		List<String> tags,
		String note
) {
	public MoodView {
		tags = List.copyOf(tags);
	}

	public static MoodView from(Mood mood) {
		return new MoodView(
				mood.getScore(),
				mood.getTags(),
				mood.getNote()
		);
	}
}
