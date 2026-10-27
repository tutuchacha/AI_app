package com.tutuchacha.ai.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 知识库视图：时间统一本地 ISO-8601，前端自行格式化。 */
@Schema(description = "知识库")
public record KnowledgeBaseView(
        Long id,
        String name,
        String description,
        String embeddingModel,
        Integer chunkSize,
        Integer chunkOverlap,
        Integer documentCount,
        String createdAt,
        String updatedAt
) {
}
