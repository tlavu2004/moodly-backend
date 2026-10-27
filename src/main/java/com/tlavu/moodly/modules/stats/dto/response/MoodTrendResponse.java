package com.tlavu.moodly.modules.stats.dto.response;

import com.tlavu.moodly.modules.stats.service.view.MoodTrendView;
import java.time.LocalDate;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"date", "averageScore", "entryCount"})
public record MoodTrendResponse(LocalDate date, double averageScore, long entryCount) {
	public static MoodTrendResponse from(MoodTrendView view) {
		return new MoodTrendResponse(view.date(), view.averageScore(), view.entryCount());
	}
}
