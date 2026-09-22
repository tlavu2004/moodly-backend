package com.tlavu.moodly.modules.stats.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"habitId", "missedCount"})
public record MostMissedHabitResponse(String habitId, long missedCount) {
}
