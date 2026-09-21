package com.tlavu.moodly.shared.presentation.dto.response;

import java.util.List;
import org.springframework.data.domain.Page;

public record PageResponse<T>(
		List<T> items,
		int page,
		int size,
		long totalElements,
		int totalPages,
		boolean hasNext
) {
	public static <T> PageResponse<T> from(Page<T> page) {
		return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.hasNext());
	}

	public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalElements) {
		int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
		return new PageResponse<>(items, page, size, totalElements, totalPages, page + 1 < totalPages);
	}
}
