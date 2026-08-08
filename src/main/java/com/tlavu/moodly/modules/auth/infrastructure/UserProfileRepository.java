package com.tlavu.moodly.modules.auth.infrastructure;

import com.tlavu.moodly.modules.auth.domain.UserProfile;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserProfileRepository extends MongoRepository<UserProfile, String> {

	Optional<UserProfile> findByAuth0Subject(String auth0Subject);
}
