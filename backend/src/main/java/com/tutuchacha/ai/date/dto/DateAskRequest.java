package com.tutuchacha.ai.date.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 约会问答：问题必填，区域可选（如「西湖」），对象档案可选。 */
@Schema(description = "约会问答")
public record DateAskRequest(
        @NotBlank(message = "问题不能为空")
        @Size(max = 200, message = "问题最多 200 字")
        @Schema(description = "自然语言问题", example = "今天去哪里玩，晚上吃个饭") String question,
        @Schema(description = "区域 / 商圈，可选；不填则由模型从问题里提取", example = "西湖") String area,
        @Schema(description = "对象档案 ID，可选；带上会参考对方兴趣与阶段", example = "1") Long targetId
) {
}
