package com.tutuchacha.ai.date.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 时间线一条：建档 / 建议 / 执行 / 阶段变更统一成这个形状，前端只按 type 配色。 */
@Schema(description = "恋爱助手：时间线事件")
public record DateTimelineEvent(
        @Schema(description = "TARGET / ADVICE / ACTION / STAGE") String type,
        @Schema(description = "类型中文") String typeLabel,
        String title,
        String detail,
        @Schema(description = "发生时间，ISO") String at
) {
}
