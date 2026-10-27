package com.tutuchacha.ai.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 回答引用的知识库片段：id 为切片 ID，title 为来源文件名。
 * 前端按该结构渲染引用卡片，刷新后从 message.citation_json 还原。
 */
@Schema(description = "回答引用的知识库片段")
public record Citation(
        @Schema(description = "切片 ID") String id,
        @Schema(description = "来源文件名") String title,
        @Schema(description = "片段摘要") String snippet,
        @Schema(description = "相似度") Double score
) {
}
