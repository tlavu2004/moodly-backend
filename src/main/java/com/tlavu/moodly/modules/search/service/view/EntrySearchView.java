package com.tlavu.moodly.modules.search.service.view;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record EntrySearchView(
		String entryId,
		LocalDate date,
		Map<String, List<HighlightFragmentView>> highlights
) {}
