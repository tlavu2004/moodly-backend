package com.tlavu.moodly.modules.auth.model;

import java.time.Duration;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "pending_asset_deletions")
@Getter
@NoArgsConstructor
public class PendingAssetDeletion {
	private static final Duration RETRY_DELAY = Duration.ofMinutes(1);
	private static final Duration ACTIVE_RECHECK_DELAY = Duration.ofMinutes(5);
	private static final Duration ACTIVE_TASK_RETENTION = Duration.ofHours(24);

	@Id
	@SuppressWarnings("unused") // Assigned by Spring Data MongoDB after persistence.
	private String id;

	@Indexed(unique = true)
	private String publicId;

	@Indexed
	private Instant nextAttemptAt;
	private Instant createdAt;

	private int cleanupAttempts;

	public PendingAssetDeletion(String publicId, Instant nextAttemptAt) {
		this.publicId = publicId;
		this.nextAttemptAt = nextAttemptAt;
		this.createdAt = Instant.now();
	}

	public void recordFailure(Instant now) {
		cleanupAttempts++;
		nextAttemptAt = now.plus(RETRY_DELAY);
	}

	/** Keep an in-flight replacement safe; eventually discard a task for an active asset. */
	public boolean recordStillActive(Instant now) {
		if (createdAt == null) {
			createdAt = now; // Existing records predate the retention field.
		}
		if (!now.isBefore(createdAt.plus(ACTIVE_TASK_RETENTION))) {
			return false;
		}
		nextAttemptAt = now.plus(ACTIVE_RECHECK_DELAY);
		return true;
	}
}
