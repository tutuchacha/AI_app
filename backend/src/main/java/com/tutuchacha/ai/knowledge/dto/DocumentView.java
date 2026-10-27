package com.tutuchacha.ai.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 文档视图：status 为 PENDING/PARSING/INDEXING/READY/FAILED。 */
@Schema(description = "知识库文档")
public record DocumentView(
        Long id,
        Long kbId,
        String filename,
        String ext,
        Long size,
        String status,
        Integer chunkCount,
        String errorMsg,
        String createdAt,
        String updatedAt
) {
}
