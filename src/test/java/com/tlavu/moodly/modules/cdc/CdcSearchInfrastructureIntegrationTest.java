package com.tlavu.moodly.modules.cdc;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.tlavu.moodly.modules.search.infrastructure.DailyEntrySearchIndexManager;
import com.tlavu.moodly.support.ElasticsearchTestConfiguration;
import com.tlavu.moodly.support.MongoTestConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import com.tlavu.moodly.support.CdcSearchTestSupport;

@SpringBootTest
@ActiveProfiles({"test", "cdc-test"})
@Import({MongoTestConfiguration.class, ElasticsearchTestConfiguration.class})
class CdcSearchInfrastructureIntegrationTest {

	@Autowired
	private ElasticsearchClient elasticsearchClient;
	@Autowired
	private DailyEntrySearchIndexManager indexManager;
	@Autowired
	private CdcSearchTestSupport cdcSearchTestSupport;

	@AfterEach
	void cleanUp() throws Exception {
		cdcSearchTestSupport.clearState();
	}

	@Test
	void createsTheConfiguredSearchIndexAgainstTestcontainers() throws Exception {
		assertThat(elasticsearchClient.indices().exists(request -> request.index(indexManager.getIndexName())).value())
				.isTrue();
	}
}
