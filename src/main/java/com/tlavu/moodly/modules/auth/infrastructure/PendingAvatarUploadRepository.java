package com.tlavu.moodly.modules.auth.infrastructure;

import com.tlavu.moodly.modules.auth.domain.PendingAvatarUpload;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PendingAvatarUploadRepository extends MongoRepository<PendingAvatarUpload, String> {
	List<PendingAvatarUpload> findByExpiresAtBefore(Instant instant);
	Optional<PendingAvatarUpload> findByPublicIdAndAuth0Subject(String publicId, String auth0Subject);
}
