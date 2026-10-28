package com.tutuchacha.ai.date.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 目标对象视图：stageLabel 给前端直接展示中文。 */
@Schema(description = "恋爱助手：目标对象")
public record DateTargetView(
        Long id,
        String name,
        String channel,
        @Schema(description = "MET / CHATTING / INVITED / DATING / TOGETHER / PAUSED") String stage,
        @Schema(description = "阶段中文名") String stageLabel,
        Integer age,
        String city,
        String interests,
        String taboos,
        String notes,
        @Schema(description = "最近联系日期") String lastContactAt,
        String createdAt,
        String updatedAt
) {
}
