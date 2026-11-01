package com.tlavu.moodly.modules.search.service.port;

import com.tlavu.moodly.modules.search.service.input.EntrySearchInput;
import com.tlavu.moodly.modules.search.service.view.EntrySearchPageView;

public interface EntrySearchQueryPort {
	EntrySearchPageView search(String userId, EntrySearchInput input, int offset);
}
