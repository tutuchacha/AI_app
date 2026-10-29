package com.tutuchacha.ai.date.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 复盘结论；不自动改阶段，前端可一键应用 suggestedStage。 */
@Schema(description = "复盘结论")
public record DateReviewView(
        @Schema(description = "CONTINUE / ADJUST / SLOW_DOWN / PAUSE") String verdict,
        @Schema(description = "判定中文") String verdictLabel,
        @Schema(description = "为什么这么判断") String reason,
        @Schema(description = "下一步做什么") String nextStep,
        @Schema(description = "建议阶段") String suggestedStage,
        @Schema(description = "建议阶段中文") String suggestedStageLabel,
        @Schema(description = "是否与当前阶段不同") boolean stageChanged,
        String model,
        int promptTokens,
        int completionTokens,
        long latencyMs
) {
}
