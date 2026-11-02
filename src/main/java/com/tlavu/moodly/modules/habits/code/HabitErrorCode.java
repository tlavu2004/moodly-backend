package com.tlavu.moodly.modules.habits.code;

import com.tlavu.moodly.shared.error.code.contract.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum HabitErrorCode implements ErrorCode {
	HABIT_ARCHIVED("HABIT_ARCHIVED", "Archived habits cannot receive new logs.", HttpStatus.CONFLICT);

	private final String code;
	private final String defaultMessage;
	private final HttpStatus status;
}
