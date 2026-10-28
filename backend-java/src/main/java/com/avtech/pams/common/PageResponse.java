package com.avtech.pams.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** {"data": [...], "pagination": {...}} — the paged list shape the API has always used. */
public record PageResponse<T>(List<T> data, Pagination pagination) {

    public record Pagination(long total, int perPage, int currentPage, int lastPage) {
    }

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                new Pagination(page.getTotalElements(), page.getSize(), page.getNumber() + 1,
                        Math.max(1, page.getTotalPages())));
    }
}
