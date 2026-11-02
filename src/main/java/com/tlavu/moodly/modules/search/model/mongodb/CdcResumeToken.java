package com.tlavu.moodly.modules.search.model.mongodb;

import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "cdc_resume_tokens")
@Getter
@Setter
@NoArgsConstructor
public class CdcResumeToken {
	@Id
	private String id;
	private String token;
	private Instant resumeAt;
	private Instant updatedAt;

	public CdcResumeToken(String id, String token, Instant updatedAt) {
		this.id = id;
		this.token = token;
		this.updatedAt = updatedAt;
	}

	public static CdcResumeToken fromOperationTime(String id, Instant resumeAt, Instant updatedAt) {
		CdcResumeToken checkpoint = new CdcResumeToken(id, null, updatedAt);
		checkpoint.resumeAt = resumeAt;
		return checkpoint;
	}
}
