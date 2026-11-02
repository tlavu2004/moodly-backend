package com.tlavu.moodly.modules.stats.code;

import com.tlavu.moodly.shared.error.code.contract.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum StatsErrorCode implements ErrorCode {
	STATS_INVALID_PERIOD("STATS_INVALID_PERIOD", "Unsupported mood trend period. Supported values: week.", HttpStatus.BAD_REQUEST);

	private final String code;
	private final String defaultMessage;
	private final HttpStatus status;
}
