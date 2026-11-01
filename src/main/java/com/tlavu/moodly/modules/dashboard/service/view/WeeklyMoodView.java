package com.tlavu.moodly.modules.dashboard.service.view;

public record WeeklyMoodView(
		Double averageScore,
		long entryCount
) {}
