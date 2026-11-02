package com.tlavu.moodly.modules.habits.exception;

import com.tlavu.moodly.modules.habits.code.HabitErrorCode;
import com.tlavu.moodly.shared.error.exception.base.ApplicationException;

public final class ArchivedHabitException extends ApplicationException {
	public ArchivedHabitException() {
		super(HabitErrorCode.HABIT_ARCHIVED);
	}
}
