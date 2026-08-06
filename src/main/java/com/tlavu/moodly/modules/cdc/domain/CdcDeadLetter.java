package com.tlavu.moodly.modules.cdc.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "cdc_dead_letters")
@Getter
@Setter
public class CdcDeadLetter {

	@Id
	private String id;
	private String eventId;
	private String operationType;
	private String entryId;
	private String payload;
	private String error;
	private int attempts;
	private Instant failedAt;

	@SuppressWarnings("unused") // Spring Data MongoDB instantiates documents through reflection.
	public CdcDeadLetter() {
	}

	public CdcDeadLetter(
			String eventId,
			String operationType,
			String entryId,
			String payload,
			String error,
			int attempts,
			Instant failedAt
	) {
		this.eventId = eventId;
		this.operationType = operationType;
		this.entryId = entryId;
		this.payload = payload;
		this.error = error;
		this.attempts = attempts;
		this.failedAt = failedAt;
	}
}
