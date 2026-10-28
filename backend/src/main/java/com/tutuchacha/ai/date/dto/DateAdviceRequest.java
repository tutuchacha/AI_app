package com.tutuchacha.ai.date.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 生成建议：针对某个对象档案。 */
@Schema(description = "生成恋爱建议")
public record DateAdviceRequest(
        @NotNull(message = "请选择对象档案")
        @Schema(description = "对象档案 ID", example = "1") Long targetId
) {
}
