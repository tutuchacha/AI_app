package com.tutuchacha.ai.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新建/更新知识库的请求体。更新为全量覆盖，字段留空即回落默认值。
 * chunkOverlap 与 chunkSize 的大小关系在 service 里校验（跨字段）。
 */
@Schema(description = "知识库基本信息")
public record KnowledgeBaseRequest(
        @Schema(description = "名称，1-64 字", example = "产品手册")
        @NotBlank(message = "不能为空")
        @Size(max = 64, message = "最长 64 字")
        String name,

        @Schema(description = "描述，最长 255 字")
        @Size(max = 255, message = "最长 255 字")
        String description,

        @Schema(description = "向量模型，变更需重建索引", example = "text-embedding-v3")
        @Size(max = 64, message = "最长 64 字")
        String embeddingModel,

        @Schema(description = "切片字符数，100-4000", example = "800")
        @Min(value = 100, message = "不能小于 100")
        @Max(value = 4000, message = "不能大于 4000")
        Integer chunkSize,

        @Schema(description = "切片重叠字符数，0-2000 且小于 chunkSize", example = "120")
        @Min(value = 0, message = "不能小于 0")
        @Max(value = 2000, message = "不能大于 2000")
        Integer chunkOverlap
) {
}
