package com.tutuchacha.ai.date.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 执行记录视图；label 由后端下发，前端不用维护枚举文案。 */
@Schema(description = "恋爱助手：执行与反馈")
public record DateActionView(
        Long id,
        Long targetId,
        Long adviceId,
        String title,
        String detail,
        @Schema(description = "计划日期") String plannedAt,
        @Schema(description = "PLANNED / DONE / SKIPPED") String status,
        @Schema(description = "执行状态中文") String statusLabel,
        @Schema(description = "UNKNOWN / GOOD / NEUTRAL / COLD / NO_REPLY") String result,
        @Schema(description = "反馈中文") String resultLabel,
        String note,
        String doneAt,
        String createdAt
) {
}
