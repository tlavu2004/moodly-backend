package com.tlavu.moodly.modules.search.service.view;

import java.util.List;

public record EntrySearchPageView(
		List<EntrySearchView> items,
		int page,
		int size,
		long totalElements
) {}
