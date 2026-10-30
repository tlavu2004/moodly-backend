package com.tlavu.moodly.modules.search.service.indexing;

import com.tlavu.moodly.modules.entries.repository.DailyEntryRepository;
import com.tlavu.moodly.modules.entries.model.DailyEntry;
import com.tlavu.moodly.modules.search.infrastructure.elasticsearch.DailyEntrySearchIndexManager;
import com.tlavu.moodly.modules.search.infrastructure.elasticsearch.DailyEntrySearchWriter;
import com.tlavu.moodly.modules.search.service.view.ReindexView;
import com.tlavu.moodly.shared.error.exception.common.SearchInfrastructureUnavailableException;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
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

	public ReindexView reindex() {
		try {
			indexManager.recreate();
			PageRequest pageRequest = PageRequest.of(0, batchSize);
			Page<DailyEntry> page = dailyEntryRepository.findAll(pageRequest);
			long indexed = 0;
			while (true) {
				for (DailyEntry entry : page.getContent()) {
					searchWriter.index(entry);
					indexed++;
				}
				if (!page.hasNext()) {
					return new ReindexView(indexed);
				}
				page = dailyEntryRepository.findAll(page.nextPageable());
			}
		} catch (IOException | co.elastic.clients.elasticsearch._types.ElasticsearchException exception) {
			throw SearchInfrastructureUnavailableException.duringReindex(exception);
		}
	}
}
