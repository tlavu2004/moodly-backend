package com.tlavu.moodly.modules.habits.exception;

import com.tlavu.moodly.modules.habits.code.HabitErrorCode;
import com.tlavu.moodly.shared.error.exception.base.ApplicationException;

public final class HabitException extends ApplicationException {
	private HabitException(HabitErrorCode code) {
		super(code);
	}

	private HabitException(HabitErrorCode code, String safeMessage, Throwable cause) {
		super(code, safeMessage, cause);
	}

	public static HabitException notFound() {
		return new HabitException(HabitErrorCode.HABIT_NOT_FOUND);
	}

	public static HabitException conflict() {
		return new HabitException(HabitErrorCode.HABIT_CONFLICT);
	}

	public static HabitException optimisticLock(Throwable cause) {
		return new HabitException(HabitErrorCode.HABIT_CONFLICT,
				"Resource was changed by another request. Refresh and retry.", cause);
	}

	public static HabitException invalidStatus() {
		return new HabitException(HabitErrorCode.HABIT_INVALID_STATUS);
	}

	public static HabitException archived() {
		return new HabitException(HabitErrorCode.HABIT_ARCHIVED);
	}
}
