package com.tlavu.moodly.modules.cdc;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.tlavu.moodly.modules.cdc.domain.CdcDeadLetter;
import com.tlavu.moodly.modules.cdc.infrastructure.CdcDeadLetterRepository;
import com.tlavu.moodly.modules.entries.domain.DailyEntry;
import com.tlavu.moodly.modules.entries.infrastructure.DailyEntryRepository;
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
import java.time.Instant;
import java.time.LocalDate;

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
	@Autowired
	private DailyEntryRepository dailyEntryRepository;
	@Autowired
	private CdcDeadLetterRepository deadLetterRepository;

	@AfterEach
	void cleanUp() throws Exception {
		cdcSearchTestSupport.clearState();
	}

	@Test
	void createsTheConfiguredSearchIndexAgainstTestcontainers() throws Exception {
		assertThat(elasticsearchClient.indices().exists(request -> request.index(indexManager.getIndexName())).value())
				.isTrue();
	}

	@Test
	void sharedSupportDrivesCdcPollingAndCleanup() throws Exception {
		var entry = cdcSearchTestSupport.save(new DailyEntry(cdcSearchTestSupport.newUserId(), LocalDate.now()));
		cdcSearchTestSupport.awaitIndexed(entry.getId());

		dailyEntryRepository.delete(entry);
		cdcSearchTestSupport.awaitRemoved(entry.getId());

		var eventId = "support-dead-letter";
		deadLetterRepository.save(new CdcDeadLetter(eventId, "delete", entry.getId(), null, "test", 1, Instant.now()));
		cdcSearchTestSupport.awaitDeadLetter(eventId);
	}
}
