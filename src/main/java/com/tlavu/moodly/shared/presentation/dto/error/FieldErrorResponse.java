package com.tlavu.moodly.shared.presentation.dto.error;

public record FieldErrorResponse(
        String field,
        String message
) {}
