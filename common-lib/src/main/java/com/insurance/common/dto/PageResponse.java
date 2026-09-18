package com.insurance.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Stable pagination envelope. Spring's {@link Page} JSON is an implementation detail that changes
 * between versions, so we never serialise it directly.
 *
 * @param content       the items of the requested page
 * @param page          zero-based page index
 * @param size          requested page size
 * @param totalElements total number of matching items
 * @param totalPages    total number of pages
 * @param last          true if this is the final page
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages, boolean last) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    /** Maps a page of entities to a page of DTOs without loading anything extra. */
    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return from(page.map(mapper));
    }
}
