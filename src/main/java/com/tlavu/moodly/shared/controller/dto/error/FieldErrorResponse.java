package com.tlavu.moodly.shared.controller.dto.error;

public record FieldErrorResponse(
        String field,
        String message
) {}
