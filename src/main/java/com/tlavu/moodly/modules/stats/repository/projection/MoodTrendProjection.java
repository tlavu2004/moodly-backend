package com.tlavu.moodly.modules.stats.repository.projection;

import java.time.LocalDate;

public record MoodTrendProjection(
		LocalDate date,
		double averageScore,
		long entryCount
) {}
