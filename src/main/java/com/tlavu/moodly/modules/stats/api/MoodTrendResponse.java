package com.tlavu.moodly.modules.stats.api;

import java.time.LocalDate;

public record MoodTrendResponse(LocalDate date, double averageScore, long entryCount) {
}
