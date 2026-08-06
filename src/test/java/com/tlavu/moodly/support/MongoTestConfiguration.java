package com.tlavu.moodly.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class MongoTestConfiguration {

	@Bean
	@ServiceConnection
	@SuppressWarnings("resource") // Spring manages the container lifecycle.
	MongoDBContainer mongoDbContainer(@Value("${moodly.testcontainers.mongodb-image}") String imageName) {
		return new MongoDBContainer(DockerImageName.parse(imageName))
				.withReplicaSet();
	}
}
