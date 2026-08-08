package com.tlavu.moodly.modules.auth.domain;

import java.time.Instant;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Application-owned profile data; Auth0 remains the authority for credentials and sessions. */
@Document(collection = "users")
@Getter
public class UserProfile {

	@Id
	@SuppressWarnings("unused") // Assigned by Spring Data MongoDB after persistence.
	private String id;

	@Indexed(name = "auth0_subject_unique", unique = true)
	private String auth0Subject;

	@Indexed(name = "email_unique", unique = true, sparse = true)
	private String email;

	private String avatarPublicId;
	private Long avatarVersion;
	private String avatarContentType;
	private Long avatarSizeBytes;
	private Instant createdAt;
	private Instant updatedAt;

	@SuppressWarnings("unused") // Required by Spring Data MongoDB for document materialization.
	public UserProfile() {
	}

	public UserProfile(String auth0Subject, String email, Instant createdAt) {
		this.auth0Subject = auth0Subject;
		this.email = email;
		this.createdAt = createdAt;
		this.updatedAt = createdAt;
	}

	public void replaceAvatar(String publicId, long version, String contentType, long sizeBytes, Instant now) {
		this.avatarPublicId = publicId;
		this.avatarVersion = version;
		this.avatarContentType = contentType;
		this.avatarSizeBytes = sizeBytes;
		this.updatedAt = now;
	}
}
