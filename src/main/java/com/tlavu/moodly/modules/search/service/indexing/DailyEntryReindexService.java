package com.tlavu.moodly.modules.search.service.indexing;

import com.tlavu.moodly.modules.entries.model.DailyEntry;
import com.tlavu.moodly.modules.search.infrastructure.elasticsearch.DailyEntrySearchIndexManager;
import com.tlavu.moodly.modules.search.infrastructure.elasticsearch.DailyEntrySearchWriter;
import com.tlavu.moodly.modules.search.infrastructure.elasticsearch.ElasticsearchFailureClassifier;
import com.tlavu.moodly.modules.search.service.view.ReindexView;
import com.tlavu.moodly.shared.error.exception.common.SearchInfrastructureUnavailableException;
import java.io.IOException;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import java.util.stream.Stream;
import java.util.Iterator;
import org.springframework.stereotype.Service;

@Service
public class DailyEntryReindexService {
	private final MongoTemplate mongoTemplate;
	private final DailyEntrySearchWriter searchWriter;
	private final DailyEntrySearchIndexManager indexManager;
	private final int batchSize;

	public DailyEntryReindexService(
			MongoTemplate mongoTemplate,
			DailyEntrySearchWriter searchWriter,
			DailyEntrySearchIndexManager indexManager,
			@Value("${moodly.cdc.reindex.batch-size}") int batchSize
	) {
		this.mongoTemplate = mongoTemplate;
		this.searchWriter = searchWriter;
		this.indexManager = indexManager;
		this.batchSize = batchSize;
	}

	public ReindexView reindex() {
		try {
			indexManager.recreate();

			Query query = new Query().with(Sort.by(Sort.Direction.ASC, "_id"))
					.cursorBatchSize(batchSize);
			long indexed = 0;
			try (Stream<DailyEntry> entries = mongoTemplate.stream(query, DailyEntry.class)) {
				Iterator<DailyEntry> cursor = entries.iterator();
				while (cursor.hasNext()) {
					searchWriter.index(cursor.next());
					indexed++;
				}
			}
			return new ReindexView(indexed);
		} catch (IOException exception) {
			throw SearchInfrastructureUnavailableException.duringReindex(exception);
		} catch (ElasticsearchException exception) {
			if (ElasticsearchFailureClassifier.isUnavailable(exception)) {
				throw SearchInfrastructureUnavailableException.duringReindex(exception);
			}

			throw exception;
		}
	}
}
