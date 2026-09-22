package com.tlavu.moodly.modules.habits.domain;

import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import io.swagger.v3.oas.annotations.media.Schema;

@Document(collection = "habits")
@CompoundIndex(name = "user_active_idx", def = "{ 'userId': 1, 'active': 1 }")
@Getter
public class Habit {

	@Id
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	private String id;
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	private String userId;
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	private String name;
	@Schema(nullable = true)
	private String icon;
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	private TargetFrequency targetFrequency;
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	private boolean active;
	@Version
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	private Long version;

	public Habit() {
	}

	public Habit(String id, String userId, String name, String icon, TargetFrequency targetFrequency, boolean active) {
		this.id = id;
		this.userId = userId;
		this.name = name;
		this.icon = icon;
		this.targetFrequency = targetFrequency;
		this.active = active;
	}

	public void update(String name, String icon) {
		this.name = name;
		this.icon = icon;
	}

	public void archive() {
		this.active = false;
	}

	public void restore() {
		this.active = true;
	}

}
