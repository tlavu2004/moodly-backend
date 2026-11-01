package com.tlavu.moodly.modules.stats.service.input;

import com.tlavu.moodly.modules.stats.domain.MoodTrendPeriod;
import java.time.LocalDate;

public record FindMoodTrendInput(
		MoodTrendPeriod period,
		LocalDate today
) {}
