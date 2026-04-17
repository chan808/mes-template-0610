package io.github.chan808.authtemplate.common

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.domain.Page

@Schema(description = "페이지네이션 응답")
data class PageResponse<T>(
    @Schema(description = "현재 페이지 데이터 목록")
    val content: List<T>,

    @Schema(description = "현재 페이지 번호 (0-based)", example = "0")
    val page: Int,

    @Schema(description = "페이지 크기", example = "20")
    val size: Int,

    @Schema(description = "전체 데이터 수", example = "100")
    val totalElements: Long,

    @Schema(description = "전체 페이지 수", example = "5")
    val totalPages: Int,

    @Schema(description = "첫 번째 페이지 여부", example = "true")
    val first: Boolean,

    @Schema(description = "마지막 페이지 여부", example = "false")
    val last: Boolean,
) {
    companion object {
        fun <T : Any> from(page: Page<T>): PageResponse<T> = PageResponse(
            content = page.content,
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            first = page.isFirst,
            last = page.isLast,
        )
    }
}
