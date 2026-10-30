package com.tlavu.moodly.modules.search.service.input;

import java.time.LocalDate;

public record EntrySearchInput(
		String query,
		LocalDate from,
		LocalDate to,
		int page,
		int size
) {}
