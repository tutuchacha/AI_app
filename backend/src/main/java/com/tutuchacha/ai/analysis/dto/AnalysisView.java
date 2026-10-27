package com.tutuchacha.ai.analysis.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 分析任务视图：status 为 PENDING/RUNNING/READY/FAILED，content 为 Markdown。 */
@Schema(description = "文档分析任务")
public record AnalysisView(
        Long id,
        Long documentId,
        Long kbId,
        String type,
        String status,
        String content,
        String model,
        Integer promptTokens,
        Integer completionTokens,
        Integer latencyMs,
        String errorMsg,
        String createdAt,
        String updatedAt
) {
}
