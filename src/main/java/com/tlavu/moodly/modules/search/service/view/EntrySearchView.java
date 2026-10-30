package com.tlavu.moodly.modules.search.service.view;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record EntrySearchView(
		String entryId,
		LocalDate date,
		Map<String, List<HighlightFragment>> highlights
) {
	public record HighlightFragment(String text, List<HighlightRange> ranges) {}

	public record HighlightRange(int start, int end) {}
}
