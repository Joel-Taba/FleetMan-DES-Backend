package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import java.util.List;

/**
 * Alias aligné sur {@code PageResult<T>} du common-core Kernel.
 * Structure identique à {@link PageResponse} pour cohérence inter-modules.
 */
public record PageResult<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        boolean empty
) {
    public static <T> PageResult<T> from(PageResponse<T> page) {
        return new PageResult<>(
                page.content(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages(),
                page.first(),
                page.last(),
                page.empty()
        );
    }
}
