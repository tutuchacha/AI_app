package com.tutuchacha.ai.date.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 建议视图：把落库的两段 JSON 还原成结构化字段给前端。 */
@Schema(description = "恋爱助手：AI 建议")
public record DateAdviceView(
        Long id,
        Long targetId,
        @Schema(description = "出建议时的阶段") String stage,
        @Schema(description = "阶段中文名") String stageLabel,
        @Schema(description = "城市，用于地点推荐") String city,
        String goal,
        List<AdviceContent.Action> actions,
        List<AdviceContent.Script> scripts,
        List<String> avoid,
        @Schema(description = "成功判断标准") String success,
        @Schema(description = "高德推荐地点") List<DateSpot> spots,
        String model,
        int promptTokens,
        int completionTokens,
        long latencyMs,
        String createdAt
) {
}
