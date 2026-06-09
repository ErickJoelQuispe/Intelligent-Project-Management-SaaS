package com.epm.task.domain.model;

import java.util.List;

/**
 * Domain-owned pagination result — replaces {@code org.springframework.data.domain.Page}
 * in domain and application layers to keep them Spring-free.
 *
 * <p>JSON shape matches Spring {@code Page} fields consumed by the frontend:
 * {@code content}, {@code totalElements}, {@code totalPages}, {@code size}, {@code number}.
 */
public record PageResult<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int size,
        int number) {

    /**
     * Maps each element to a new type, preserving pagination metadata.
     */
    public <R> PageResult<R> map(java.util.function.Function<T, R> mapper) {
        return new PageResult<>(
                content.stream().map(mapper).toList(),
                totalElements,
                totalPages,
                size,
                number);
    }
}
