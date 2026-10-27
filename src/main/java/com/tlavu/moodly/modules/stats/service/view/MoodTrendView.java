package com.tlavu.moodly.modules.stats.service.view;

import com.tlavu.moodly.modules.stats.repository.projection.MoodTrendProjection;
import java.time.LocalDate;

public record MoodTrendView(LocalDate date, double averageScore, long entryCount) {
	public static MoodTrendView from(MoodTrendProjection projection) {
		return new MoodTrendView(projection.date(), projection.averageScore(), projection.entryCount());
	}
}
