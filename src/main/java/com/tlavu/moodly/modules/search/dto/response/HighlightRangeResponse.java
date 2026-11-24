package com.tlavu.moodly.modules.search.dto.response;

import com.tlavu.moodly.modules.search.service.view.HighlightRangeView;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
		name = "HighlightRange",
		requiredProperties = {"start", "end"}
)
public record HighlightRangeResponse(
		@Schema(description = "Zero-based inclusive UTF-16 offset into the containing fragment's text.") int start,
		@Schema(description = "Exclusive UTF-16 offset into the containing fragment's text.") int end
) {
	public static HighlightRangeResponse from(HighlightRangeView view) {
		return new HighlightRangeResponse(view.start(), view.end());
	}
}
