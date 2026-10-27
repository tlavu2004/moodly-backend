package com.tlavu.moodly.modules.stats.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"habitId", "missedCount"})
public record MostMissedHabitResponse(String habitId, long missedCount) {
}
