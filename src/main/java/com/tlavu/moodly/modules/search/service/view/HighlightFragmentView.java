package com.tlavu.moodly.modules.search.service.view;

import java.util.List;

public record HighlightFragmentView(String text, List<HighlightRangeView> ranges) {}
