package com.tlavu.moodly.modules.search.dto.response;

import com.tlavu.moodly.modules.search.service.view.HighlightFragmentView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(
		name = "HighlightFragment",
		requiredProperties = {"text", "ranges"}
)
public record HighlightFragmentResponse(String text, List<HighlightRangeResponse> ranges) {
	public static HighlightFragmentResponse from(HighlightFragmentView view) {
		return new HighlightFragmentResponse(
				view.text(),
				view.ranges().stream()
						.map(HighlightRangeResponse::from)
						.toList()
		);
	}
}
