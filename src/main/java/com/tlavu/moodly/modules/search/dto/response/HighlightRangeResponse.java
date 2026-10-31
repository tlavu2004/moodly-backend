package com.tlavu.moodly.modules.search.dto.response;

import com.tlavu.moodly.modules.search.service.view.HighlightRangeView;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "HighlightRange", requiredProperties = {"start", "end"})
public record HighlightRangeResponse(int start, int end) {

	static HighlightRangeResponse from(HighlightRangeView view) {
		return new HighlightRangeResponse(view.start(), view.end());
	}
}
