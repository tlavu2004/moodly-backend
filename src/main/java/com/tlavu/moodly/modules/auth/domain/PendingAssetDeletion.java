package com.tlavu.moodly.modules.auth.domain;

import java.time.Duration;
import java.time.Instant;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "pending_asset_deletions")
@Getter
public class PendingAssetDeletion {
	private static final Duration RETRY_DELAY = Duration.ofMinutes(1);

	@Id
	@SuppressWarnings("unused") // Assigned by Spring Data MongoDB after persistence.
	private String id;
	@Indexed(unique = true) private String publicId;
	@Indexed private Instant nextAttemptAt;
	private int cleanupAttempts;

	@SuppressWarnings("unused") // Required by Spring Data MongoDB for document materialization.
	public PendingAssetDeletion() {}

	public PendingAssetDeletion(String publicId, Instant nextAttemptAt) {
		this.publicId = publicId;
		this.nextAttemptAt = nextAttemptAt;
	}

	public void recordFailure(Instant now) {
		cleanupAttempts++;
		nextAttemptAt = now.plus(RETRY_DELAY);
	}
}
