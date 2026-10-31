package com.tlavu.moodly.modules.search.dto.response;

import com.tlavu.moodly.modules.search.service.view.EntrySearchView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Schema(
		name = "EntrySearchResult",
		requiredProperties = {"entryId", "date", "highlights"}
)
public record EntrySearchResponse(
		String entryId,
		LocalDate date,
		@Schema(
				description = "Plain-text fragments keyed only by mood.note, habits.note, or mood.tags; ranges use zero-based, end-exclusive offsets."
		) Map<String, List<HighlightFragmentResponse>> highlights
) {
	public static EntrySearchResponse from(EntrySearchView view) {
		Map<String, List<HighlightFragmentResponse>> highlights = new LinkedHashMap<>();
		view.highlights().forEach((field, fragments) ->
				highlights.put(
						field,
						fragments.stream()
								.map(HighlightFragmentResponse::from)
								.toList()
				)
		);

		return new EntrySearchResponse(
				view.entryId(),
				view.date(),
				Map.copyOf(highlights)
		);
	}
}
