package com.tlavu.moodly.modules.auth.repository;

import com.tlavu.moodly.modules.auth.model.PendingAssetDeletion;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PendingAssetDeletionRepository
		extends MongoRepository<PendingAssetDeletion, String> {
	List<PendingAssetDeletion> findByNextAttemptAtBefore(Instant instant);

	Optional<PendingAssetDeletion> findByPublicId(String publicId);
}
