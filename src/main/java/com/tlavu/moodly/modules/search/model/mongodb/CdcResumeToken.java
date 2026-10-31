package com.tlavu.moodly.modules.search.model.mongodb;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "cdc_resume_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CdcResumeToken {
	@Id
	private String id;
	private String token;
	private Instant updatedAt;
}
