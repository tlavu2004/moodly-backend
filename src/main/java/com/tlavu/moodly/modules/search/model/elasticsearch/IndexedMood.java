package com.tlavu.moodly.modules.search.model.elasticsearch;

import java.util.List;

public record IndexedMood(Integer score, String note, List<String> tags) {
}
