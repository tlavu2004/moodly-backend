package com.tlavu.moodly.modules.habits.code;

import com.tlavu.moodly.shared.error.code.contract.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum HabitErrorCode implements ErrorCode {
	HABIT_ARCHIVED("HABIT_ARCHIVED", "Archived habits cannot receive new logs.", HttpStatus.CONFLICT),
	HABIT_NOT_FOUND("HABIT_NOT_FOUND", "Habit was not found.", HttpStatus.NOT_FOUND),
	HABIT_CONFLICT("HABIT_CONFLICT", "The resource was changed by another request.", HttpStatus.CONFLICT),
	HABIT_INVALID_STATUS("HABIT_INVALID_STATUS", "status must be one of: active, archived, all", HttpStatus.BAD_REQUEST);

	private final String code;
	private final String defaultMessage;
	private final HttpStatus status;
}
