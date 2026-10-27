package com.tutuchacha.ai.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 检索命中的切片：score 为查询向量与该切片向量的余弦相似度。 */
@Schema(description = "检索命中的切片")
public record SearchHit(
        Long chunkId,
        Long documentId,
        String filename,
        Integer chunkIndex,
        Double score,
        String content
) {
}
