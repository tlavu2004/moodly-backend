package com.tlavu.moodly.modules.cdc.application;

import com.tlavu.moodly.modules.entries.infrastructure.DailyEntryRepository;
import com.tlavu.moodly.modules.search.infrastructure.DailyEntrySearchIndexManager;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class DailyEntryReindexService {

	private final DailyEntryRepository dailyEntryRepository;
	private final DailyEntrySearchWriter searchWriter;
	private final DailyEntrySearchIndexManager indexManager;
	private final int batchSize;

	public DailyEntryReindexService(
			DailyEntryRepository dailyEntryRepository,
			DailyEntrySearchWriter searchWriter,
			DailyEntrySearchIndexManager indexManager,
			@Value("${moodly.cdc.reindex.batch-size}") int batchSize
	) {
		this.dailyEntryRepository = dailyEntryRepository;
		this.searchWriter = searchWriter;
		this.indexManager = indexManager;
		this.batchSize = batchSize;
	}

	public ReindexResult reindex() throws IOException {
		indexManager.recreate();
		var pageRequest = PageRequest.of(0, batchSize);
		var page = dailyEntryRepository.findAll(pageRequest);
		long indexed = 0;
		while (true) {
			for (var entry : page.getContent()) {
				searchWriter.index(entry);
				indexed++;
			}
			if (!page.hasNext()) {
				return new ReindexResult(indexed);
			}
			page = dailyEntryRepository.findAll(page.nextPageable());
		}
	}

	public record ReindexResult(long indexedEntries) {
	}
}
