package com.tlavu.moodly.modules.entries.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HabitLog {
	private String habitId;
	private boolean done;
	private String note;
}
