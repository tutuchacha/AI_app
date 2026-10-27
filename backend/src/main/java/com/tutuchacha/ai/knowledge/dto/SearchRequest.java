package com.tutuchacha.ai.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 检索请求：topK / minScore 留空走配置默认（6 / 0.2）。
 * 只查当前用户自己的知识库，归属校验在 service 里做。
 */
@Schema(description = "知识库检索请求")
public record SearchRequest(

        @Schema(description = "自然语言问题，1-500 字", example = "年假怎么申请？")
        @NotBlank(message = "不能为空")
        @Size(max = 500, message = "最长 500 字")
        String query,

        @Schema(description = "返回条数，1-20，默认取配置", example = "6")
        @Min(value = 1, message = "不能小于 1")
        @Max(value = 20, message = "不能大于 20")
        Integer topK,

        @Schema(description = "相似度下限，0-1，默认取配置", example = "0.2")
        @DecimalMin(value = "0.0", message = "不能小于 0")
        @DecimalMax(value = "1.0", message = "不能大于 1")
        Double minScore
) {
}
