package com.tlavu.moodly.modules.auth.service.implementation;

import com.tlavu.moodly.modules.auth.model.UserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/** Updates avatar fields only when the previously observed avatar is still current. */
@Component
@RequiredArgsConstructor
public class AvatarProfileWriter {
	private final MongoTemplate mongoTemplate;

	public boolean replaceIfCurrent(UserProfile profile, String previousPublicId) {
		return mongoTemplate.updateFirst(
				currentAvatar(profile, previousPublicId),
				new Update().set("avatarPublicId", profile.getAvatarPublicId())
						.set("avatarVersion", profile.getAvatarVersion())
						.set("avatarContentType", profile.getAvatarContentType())
						.set("avatarSizeBytes", profile.getAvatarSizeBytes())
						.set("updatedAt", profile.getUpdatedAt()),
				UserProfile.class
		).getMatchedCount() == 1;
	}

	public boolean clearIfCurrent(UserProfile profile, String previousPublicId) {
		return mongoTemplate.updateFirst(
				currentAvatar(profile, previousPublicId),
				new Update().unset("avatarPublicId")
						.unset("avatarVersion")
						.unset("avatarContentType")
						.unset("avatarSizeBytes")
						.set("updatedAt", profile.getUpdatedAt()),
				UserProfile.class
		).getMatchedCount() == 1;
	}

	private Query currentAvatar(UserProfile profile, String previousPublicId) {
		return Query.query(
				Criteria.where("_id")
						.is(profile.getId())
						.and("avatarPublicId")
						.is(previousPublicId)
		);
	}
}
