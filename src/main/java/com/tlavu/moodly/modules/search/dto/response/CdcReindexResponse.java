package com.tlavu.moodly.modules.search.dto.response;

import com.tlavu.moodly.modules.search.service.view.ReindexView;

public record CdcReindexResponse(long indexedEntries) {
	public static CdcReindexResponse from(ReindexView view) {
		return new CdcReindexResponse(view.indexedEntries());
	}
}
