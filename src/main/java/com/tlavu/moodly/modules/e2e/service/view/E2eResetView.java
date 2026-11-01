package com.tlavu.moodly.modules.e2e.service.view;

public record E2eResetView(
		String userId,
		int habits,
		int entries,
		String searchIndexing
) {}
