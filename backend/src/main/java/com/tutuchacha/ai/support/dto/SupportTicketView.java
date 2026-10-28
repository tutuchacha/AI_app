package com.tutuchacha.ai.support.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 工单视图：前端列表只展示问题、状态、引用分与时间。 */
@Schema(description = "转人工工单")
public record SupportTicketView(
        @Schema(description = "工单 ID") Long id,
        @Schema(description = "用户原问题") String question,
        @Schema(description = "OPEN / CLOSED") String status,
        @Schema(description = "触发时的最高引用分") Double bestScore,
        @Schema(description = "处理备注") String note,
        @Schema(description = "会话 UID，可能为空") String conversationUid,
        @Schema(description = "创建时间") String createdAt,
        @Schema(description = "更新时间") String updatedAt
) {
}
