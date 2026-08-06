package com.tlavu.moodly.modules.search.infrastructure;

import com.tlavu.moodly.shared.application.exception.SearchInfrastructureUnavailableException;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Creates the derived daily-entry search index without changing an existing mapping. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
	name = "moodly.search.index.initialization-enabled",
	havingValue = "true",
	matchIfMissing = true
)
public class DailyEntrySearchIndexInitializer implements ApplicationRunner {

	private final DailyEntrySearchIndexManager indexManager;

	@Override
	public void run(@NonNull ApplicationArguments args) {
		try {
			indexManager.createIfMissing();
		} catch (IOException exception) {
			throw new SearchInfrastructureUnavailableException("Elasticsearch index initialization is unavailable", exception);
		}
	}
}
