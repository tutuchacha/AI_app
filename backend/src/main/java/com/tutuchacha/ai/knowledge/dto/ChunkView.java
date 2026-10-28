package com.tutuchacha.ai.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 引用溯源用的切片原文：带来源文件名与序号，前端高亮命中段。 */
@Schema(description = "切片原文")
public record ChunkView(
        @Schema(description = "切片 ID") Long id,
        @Schema(description = "文档 ID") Long documentId,
        @Schema(description = "知识库 ID") Long kbId,
        @Schema(description = "来源文件名") String filename,
        @Schema(description = "片内序号，从 0 开始") Integer chunkIndex,
        @Schema(description = "估算 token 数") Integer tokenCount,
        @Schema(description = "完整正文") String content
) {
}
