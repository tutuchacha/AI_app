package com.tutuchacha.ai.analysis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 创建分析任务的入参：type 取 SUMMARY / OUTLINE / QUESTIONS。 */
@Schema(description = "创建文档分析任务")
public record AnalysisRequest(
        @NotBlank(message = "type 必填")
        @Schema(description = "SUMMARY / OUTLINE / QUESTIONS", example = "SUMMARY")
        String type
) {
}
