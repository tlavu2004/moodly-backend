package com.tlavu.moodly.modules.habits.domain;

import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "habits")
@CompoundIndex(name = "user_active_idx", def = "{ 'userId': 1, 'active': 1 }")
@Getter
public class Habit {

	@Id
	private String id;
	private String userId;
	private String name;
	private String icon;
	private String targetFrequency;
	private boolean active;

	public Habit() {
	}

	public Habit(String id, String userId, String name, String icon, String targetFrequency, boolean active) {
		this.id = id;
		this.userId = userId;
		this.name = name;
		this.icon = icon;
		this.targetFrequency = targetFrequency;
		this.active = active;
	}

}
