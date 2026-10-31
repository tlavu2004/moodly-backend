package com.tlavu.moodly.modules.search.dto.response;

import com.tlavu.moodly.modules.search.service.view.EntrySearchView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "HighlightFragment", requiredProperties = {"text", "ranges"})
public record HighlightFragmentResponse(String text, List<HighlightRangeResponse> ranges) {

	static HighlightFragmentResponse from(EntrySearchView.HighlightFragment view) {
		return new HighlightFragmentResponse(
				view.text(),
				view.ranges().stream().map(HighlightRangeResponse::from).toList()
		);
	}
}
