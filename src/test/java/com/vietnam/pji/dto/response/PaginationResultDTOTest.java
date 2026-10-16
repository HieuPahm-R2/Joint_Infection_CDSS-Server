package com.vietnam.pji.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * Pins the paginated envelope contract that used to be copy-pasted into every
 * paginated service method.
 */
class PaginationResultDTOTest {

    @Test
    void metaReportsOneBasedPageNumbersAndUnmappedRowTotal() {
        Page<String> page = new PageImpl<>(
                List.of("a", "b"),
                PageRequest.of(2, 2),
                9);

        PaginationResultDTO result = PaginationResultDTO.of(page);

        assertThat(result.getMeta().getPage()).isEqualTo(3);
        assertThat(result.getMeta().getPageSize()).isEqualTo(2);
        assertThat(result.getMeta().getPages()).isEqualTo(5);
        assertThat(result.getMeta().getTotal()).isEqualTo(9);
        assertThat(result.getResult()).isEqualTo(List.of("a", "b"));
    }

    @Test
    void mappedRowsReplaceTheEntityContent() {
        Page<String> page = new PageImpl<>(List.of("entity"), PageRequest.of(0, 10), 1);

        PaginationResultDTO result = PaginationResultDTO.of(page, List.of("dto"));

        assertThat(result.getResult()).isEqualTo(List.of("dto"));
        // Meta always describes the database page, never the mapped row count.
        assertThat(result.getMeta().getTotal()).isEqualTo(1);
    }

    @Test
    void emptyPageStillCarriesAnEmptyResultList() {
        Page<String> page = Page.empty();

        PaginationResultDTO result = PaginationResultDTO.of(page);

        assertThat(result.getMeta().getPage()).isEqualTo(1);
        assertThat(result.getMeta().getTotal()).isZero();
        assertThat((List<?>) result.getResult()).isEmpty();
    }
}
