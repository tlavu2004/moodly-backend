package com.tlavu.moodly.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class MongoTestConfiguration {

	@Bean
	JwtDecoder testJwtDecoder() {
		return token -> {
			throw new JwtException("Real bearer tokens are not decoded in integration tests.");
		};
	}

	@Bean
	@ServiceConnection
	@SuppressWarnings("resource") // Spring manages the container lifecycle.
	MongoDBContainer mongoDbContainer(@Value("${moodly.testcontainers.mongodb-image}") String imageName) {
		return new MongoDBContainer(DockerImageName.parse(imageName))
				.withReplicaSet();
	}
}
