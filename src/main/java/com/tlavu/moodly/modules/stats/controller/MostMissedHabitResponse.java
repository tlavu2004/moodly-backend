package com.tlavu.moodly.modules.stats.controller;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"habitId", "missedCount"})
public record MostMissedHabitResponse(String habitId, long missedCount) {
}
