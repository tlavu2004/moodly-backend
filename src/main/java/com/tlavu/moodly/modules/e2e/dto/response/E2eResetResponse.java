package com.tlavu.moodly.modules.e2e.dto.response;

import com.tlavu.moodly.modules.e2e.service.view.E2eResetView;

public record E2eResetResponse(
		String userId,
		int habits,
		int entries,
		String searchIndexing
) {
	public static E2eResetResponse from(E2eResetView view) {
		return new E2eResetResponse(
				view.userId(),
				view.habits(),
				view.entries(),
				view.searchIndexing()
		);
	}
}
