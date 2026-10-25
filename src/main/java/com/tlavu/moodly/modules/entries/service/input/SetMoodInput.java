package com.tlavu.moodly.modules.entries.service.input;

import java.util.List;

public record SetMoodInput(int score, List<String> tags, String note) {

	public SetMoodInput {
		tags = tags == null ? null : List.copyOf(tags);
	}
}
