package com.yotpo.orders.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Paginated response for order list queries.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Paginated list of orders")
public class OrderListResponse {

    @Schema(description = "List of orders on this page")
    private List<OrderResponse> orders;

    @Schema(description = "Pagination metadata")
    private PageInfo pagination;

    /**
     * Pagination metadata.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Pagination information")
    public static class PageInfo {
        @Schema(description = "Current page number (0-based)", example = "0")
        private int page;

        @Schema(description = "Page size", example = "20")
        private int size;

        @Schema(description = "Total number of elements", example = "150")
        private long totalElements;

        @Schema(description = "Total number of pages", example = "8")
        private int totalPages;

        @Schema(description = "Is this the first page", example = "true")
        private boolean first;

        @Schema(description = "Is this the last page", example = "false")
        private boolean last;
    }

    /**
     * Create from Spring Data Page.
     */
    public static OrderListResponse fromPage(Page<OrderResponse> page) {
        return OrderListResponse.builder()
            .orders(page.getContent())
            .pagination(PageInfo.builder()
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build())
            .build();
    }
}
