package com.tlavu.moodly.shared.presentation.dto.response;

import java.util.List;
import org.springframework.data.domain.Page;
import io.swagger.v3.oas.annotations.media.Schema;

public record PageResponse<T>(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<T> items,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int size,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalElements,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalPages,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean hasNext
) {
	public static <T> PageResponse<T> from(Page<T> page) {
		return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.hasNext());
	}

	public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalElements) {
		int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
		return new PageResponse<>(items, page, size, totalElements, totalPages, page + 1 < totalPages);
	}
}
