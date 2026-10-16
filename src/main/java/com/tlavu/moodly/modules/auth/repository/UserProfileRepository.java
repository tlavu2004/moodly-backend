package com.tlavu.moodly.modules.auth.repository;

import com.tlavu.moodly.modules.auth.model.UserProfile;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserProfileRepository extends MongoRepository<UserProfile, String> {

	Optional<UserProfile> findByAuth0Subject(String auth0Subject);
}
