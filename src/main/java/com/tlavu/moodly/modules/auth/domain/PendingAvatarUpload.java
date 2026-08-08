package com.tlavu.moodly.modules.auth.domain;

import java.time.Instant;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "pending_avatar_uploads")
@Getter
public class PendingAvatarUpload {
	@Id
	@SuppressWarnings("unused") // Assigned by Spring Data MongoDB after persistence.
	private String id;
	@Indexed(unique = true) private String publicId;
	private String auth0Subject;
	@Indexed private Instant expiresAt;
	private int cleanupAttempts;
	@SuppressWarnings("unused") // Required by Spring Data MongoDB for document materialization.
	public PendingAvatarUpload() {}
	public PendingAvatarUpload(String publicId, String auth0Subject, Instant expiresAt) { this.publicId = publicId; this.auth0Subject = auth0Subject; this.expiresAt = expiresAt; }
	public void recordCleanupFailure() { cleanupAttempts++; }
}
