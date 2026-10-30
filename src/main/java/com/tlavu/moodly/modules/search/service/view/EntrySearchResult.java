package com.tlavu.moodly.modules.search.service.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Schema(requiredProperties = {"entryId", "date", "highlights"})
public record EntrySearchResult(
		String entryId,
		LocalDate date,
		@Schema(description = "Plain-text fragments keyed only by mood.note, habits.note, or mood.tags; ranges use zero-based, end-exclusive offsets.")
		Map<String, List<HighlightFragment>> highlights
) {
	@Schema(requiredProperties = {"text", "ranges"})
	public record HighlightFragment(String text, List<HighlightRange> ranges) {}

	@Schema(requiredProperties = {"start", "end"})
	public record HighlightRange(int start, int end) {}
}
