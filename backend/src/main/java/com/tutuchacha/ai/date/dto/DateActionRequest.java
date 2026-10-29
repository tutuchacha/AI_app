package com.tutuchacha.ai.date.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** 新建执行记录：可来自某条建议的动作，也可手写。 */
@Schema(description = "新建执行记录")
public record DateActionRequest(
        @Schema(description = "对象档案 ID", example = "1") Long targetId,
        @Schema(description = "来源建议 ID，可选", example = "3") Long adviceId,
        @NotBlank(message = "请写下要做的事")
        @Size(max = 120, message = "标题最多 120 字")
        @Schema(description = "要做的事", example = "用露营兴趣开场") String title,
        @Size(max = 500, message = "细节最多 500 字")
        @Schema(description = "怎么做 / 说到什么程度") String detail,
        @Schema(description = "计划日期，可选", example = "2026-10-30") LocalDate plannedAt
) {
}
