package com.tlavu.moodly.modules.auth.model;

import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "pending_avatar_uploads")
@Getter
@NoArgsConstructor
public class PendingAvatarUpload {
	@Id
	@SuppressWarnings("unused") // Assigned by Spring Data MongoDB after persistence.
	private String id;

	@Indexed(unique = true)
	private String publicId;

	private String auth0Subject;

	@Indexed
	private Instant expiresAt;

	private int cleanupAttempts;

	public PendingAvatarUpload(
			String publicId,
			String auth0Subject,
			Instant expiresAt
	) {
		this.publicId = publicId;
		this.auth0Subject = auth0Subject;
		this.expiresAt = expiresAt;
	}

	public void recordCleanupFailure() {
		cleanupAttempts++;
	}
}
