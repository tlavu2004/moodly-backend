package com.tlavu.moodly.modules.stats.exception;

import com.tlavu.moodly.modules.stats.code.StatsErrorCode;
import com.tlavu.moodly.shared.error.exception.base.ApplicationException;

public final class StatsException extends ApplicationException {
	private StatsException(StatsErrorCode code) {
		super(code);
	}

	public static StatsException invalidPeriod() {
		return new StatsException(StatsErrorCode.STATS_INVALID_PERIOD);
	}
}
