package com.tlavu.moodly.modules.stats.api;

import java.time.LocalDate;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"date", "averageScore", "entryCount"})
public record MoodTrendResponse(LocalDate date, double averageScore, long entryCount) {
}
