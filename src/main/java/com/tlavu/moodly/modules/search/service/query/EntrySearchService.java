package com.tlavu.moodly.modules.search.service.query;

import com.tlavu.moodly.modules.search.service.input.EntrySearchInput;
import com.tlavu.moodly.modules.search.service.view.EntrySearchPageView;
import com.tlavu.moodly.modules.search.validation.EntrySearchPagination;
import org.springframework.stereotype.Service;

@Service
public class EntrySearchService {

	private final EntrySearchQueryPort queryPort;

	public EntrySearchService(EntrySearchQueryPort queryPort) {
		this.queryPort = queryPort;
	}

	public EntrySearchPageView search(String userId, EntrySearchInput input) {
		int offset = EntrySearchPagination.validatedOffset(input.page(), input.size());
		return queryPort.search(userId, input, offset);
	}
}
