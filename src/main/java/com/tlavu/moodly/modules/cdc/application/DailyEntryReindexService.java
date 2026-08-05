package com.tlavu.moodly.modules.cdc.application;

import com.tlavu.moodly.modules.entries.infrastructure.DailyEntryRepository;
import com.tlavu.moodly.modules.search.infrastructure.DailyEntrySearchIndexManager;
import java.io.IOException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class DailyEntryReindexService {

	private static final int BATCH_SIZE = 250;

	private final DailyEntryRepository dailyEntryRepository;
	private final DailyEntrySearchWriter searchWriter;
	private final DailyEntrySearchIndexManager indexManager;

	public DailyEntryReindexService(
			DailyEntryRepository dailyEntryRepository,
			DailyEntrySearchWriter searchWriter,
			DailyEntrySearchIndexManager indexManager
	) {
		this.dailyEntryRepository = dailyEntryRepository;
		this.searchWriter = searchWriter;
		this.indexManager = indexManager;
	}

	public ReindexResult reindex() throws IOException {
		indexManager.recreate();
		var pageRequest = PageRequest.of(0, BATCH_SIZE);
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
