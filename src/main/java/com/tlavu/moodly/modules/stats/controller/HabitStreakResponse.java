package com.tlavu.moodly.modules.stats.controller;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"habitId", "currentStreak"})
public record HabitStreakResponse(String habitId, int currentStreak) {
}
