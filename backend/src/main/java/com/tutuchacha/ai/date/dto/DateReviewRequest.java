package com.tutuchacha.ai.date.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 复盘：针对某个对象，基于最近执行与反馈给下一步判断。 */
@Schema(description = "复盘请求")
public record DateReviewRequest(
        @NotNull(message = "请选择对象档案")
        @Schema(description = "对象档案 ID", example = "1") Long targetId
) {
}
