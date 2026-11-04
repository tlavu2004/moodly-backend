package com.tlavu.moodly.modules.search.config;

import com.tlavu.moodly.modules.search.infrastructure.elasticsearch.ElasticsearchException;
import com.tlavu.moodly.modules.search.infrastructure.elasticsearch.DailyEntrySearchIndexManager;
import com.tlavu.moodly.modules.search.infrastructure.elasticsearch.ElasticsearchFailureClassifier;

import com.tlavu.moodly.shared.error.exception.common.SearchInfrastructureUnavailableException;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

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

	@Value("${moodly.cdc.enabled:false}")
	private boolean cdcEnabled;

	@Override
	public void run(@NonNull ApplicationArguments args) {
		// CDC checks for a lost index before resuming its checkpoint.
		if (cdcEnabled) {
			return;
		}

		try {
			indexManager.createIfMissing();
		} catch (IOException exception) {
			throw SearchInfrastructureUnavailableException.duringIndexInitialization(exception);
		} catch (ElasticsearchException exception) {
			if (ElasticsearchFailureClassifier.isUnavailable(exception)) {
				throw SearchInfrastructureUnavailableException.duringIndexInitialization(exception);
			}

			throw exception;
		}
	}
}
