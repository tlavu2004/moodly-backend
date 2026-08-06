package com.tlavu.moodly.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class ElasticsearchTestConfiguration {

	@Bean
	CdcSearchTestSupport cdcSearchTestSupport(
			com.tlavu.moodly.modules.entries.infrastructure.DailyEntryRepository dailyEntryRepository,
			com.tlavu.moodly.modules.cdc.infrastructure.CdcDeadLetterRepository deadLetterRepository,
			org.springframework.data.mongodb.core.MongoTemplate mongoTemplate,
			co.elastic.clients.elasticsearch.ElasticsearchClient elasticsearchClient,
			com.tlavu.moodly.modules.search.infrastructure.DailyEntrySearchIndexManager indexManager
	) {
		return new CdcSearchTestSupport(
				dailyEntryRepository, deadLetterRepository, mongoTemplate, elasticsearchClient, indexManager);
	}

	@Bean
	@ServiceConnection
	@SuppressWarnings("resource") // Spring manages the container lifecycle.
	ElasticsearchContainer elasticsearchContainer(
			@Value("${moodly.testcontainers.elasticsearch-image}") String imageName
	) {
		return new ElasticsearchContainer(DockerImageName.parse(imageName))
				.withEnv("xpack.security.enabled", "false")
				.withEnv("xpack.security.http.ssl.enabled", "false");
	}
}
