package com.tlavu.moodly.shared.api.dto.error;

public record FieldErrorResponse(
        String field,
        String message
) {}
