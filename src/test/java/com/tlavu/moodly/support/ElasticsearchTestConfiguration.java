package com.tlavu.moodly.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class ElasticsearchTestConfiguration {

	@Bean
	CdcSearchTestSupport cdcSearchTestSupport(
			com.tlavu.moodly.modules.entries.repository.DailyEntryRepository dailyEntryRepository,
			com.tlavu.moodly.modules.search.repository.CdcDeadLetterRepository deadLetterRepository,
			org.springframework.data.mongodb.core.MongoTemplate mongoTemplate,
			com.tlavu.moodly.modules.search.infrastructure.elasticsearch.ElasticsearchClient elasticsearchClient,
			com.tlavu.moodly.modules.search.infrastructure.elasticsearch.DailyEntrySearchIndexManager indexManager
	) {
		return new CdcSearchTestSupport(
				dailyEntryRepository, deadLetterRepository, mongoTemplate, elasticsearchClient, indexManager);
	}

}
