package com.tlavu.moodly.modules.cdc.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "cdc_resume_tokens")
@Getter
@Setter
public class CdcResumeToken {

	@Id
	private String id;
	private String token;
	private Instant updatedAt;

	public CdcResumeToken() {
	}

	public CdcResumeToken(String id, String token, Instant updatedAt) {
		this.id = id;
		this.token = token;
		this.updatedAt = updatedAt;
	}
}
