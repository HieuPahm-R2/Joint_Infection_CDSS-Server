package com.vietnam.pji.dto.response;

import java.util.List;

import org.springframework.data.domain.Page;

import lombok.Getter;
import lombok.Setter;

/**
 * Envelope for paginated responses.
 * Build one through {@link #of(Page)} or {@link #of(Page, List)} so the
 * 1-based page number and the meta/result pairing stay identical everywhere.
 */
@Getter
@Setter
public class PaginationResultDTO {
    private Meta meta;
    private Object result;

    /**
     * @param page       the page of entities, as returned by Spring Data
     * @param mappedRows rows already converted to their response shape; pass an
     *                   empty list to publish the page content unchanged
     */
    public static PaginationResultDTO of(Page<?> page, List<?> mappedRows) {
        Meta meta = new Meta();
        meta.setPage(page.getNumber() + 1);
        meta.setPageSize(page.getSize());
        meta.setPages(page.getTotalPages());
        meta.setTotal(page.getTotalElements());

        PaginationResultDTO result = new PaginationResultDTO();
        result.setMeta(meta);
        result.setResult(mappedRows);
        return result;
    }

    /** @param page the page of entities, published as-is in {@code result} */
    public static PaginationResultDTO of(Page<?> page) {
        return of(page, page.getContent());
    }

    @Getter
    @Setter
    public static class Meta {
        private int page;
        private int pageSize;
        private int pages;
        private long total;
    }
}
