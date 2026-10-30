package com.tlavu.moodly.modules.search.dto.response;

import com.tlavu.moodly.modules.search.service.view.EntrySearchView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Schema(name = "EntrySearchResult", requiredProperties = {"entryId", "date", "highlights"})
public record EntrySearchResponse(
		String entryId,
		LocalDate date,
		@Schema(description = "Plain-text fragments keyed only by mood.note, habits.note, or mood.tags; ranges use zero-based, end-exclusive offsets.")
		Map<String, List<HighlightFragment>> highlights
) {
	public static EntrySearchResponse from(EntrySearchView view) {
		Map<String, List<HighlightFragment>> highlights = new LinkedHashMap<>();
		view.highlights().forEach((field, fragments) ->
				highlights.put(field, fragments.stream().map(HighlightFragment::from).toList()));
		return new EntrySearchResponse(view.entryId(), view.date(), Map.copyOf(highlights));
	}

	@Schema(requiredProperties = {"text", "ranges"})
	public record HighlightFragment(String text, List<HighlightRange> ranges) {
		private static HighlightFragment from(EntrySearchView.HighlightFragment view) {
			return new HighlightFragment(
					view.text(),
					view.ranges().stream().map(HighlightRange::from).toList()
			);
		}
	}

	@Schema(requiredProperties = {"start", "end"})
	public record HighlightRange(int start, int end) {
		private static HighlightRange from(EntrySearchView.HighlightRange view) {
			return new HighlightRange(view.start(), view.end());
		}
	}
}
