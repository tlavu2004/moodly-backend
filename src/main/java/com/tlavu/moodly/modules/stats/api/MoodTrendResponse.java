package com.tlavu.moodly.modules.stats.api;

import java.time.LocalDate;

public record MoodTrendResponse(LocalDate weekStart, double averageScore, long entryCount) {
}
