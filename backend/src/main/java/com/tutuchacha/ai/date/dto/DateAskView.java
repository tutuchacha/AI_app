package com.tutuchacha.ai.date.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 问答结果：模型建议 + 按城市/区域查到的高德地点。 */
@Schema(description = "约会问答结果")
public record DateAskView(
        String reply,
        @Schema(description = "本次使用的区域，可能为空") String area,
        @Schema(description = "城市，来自对象或我的档案") String city,
        @Schema(description = "本次检索关键词") List<String> keywords,
        List<DateSpot> spots,
        String model,
        int promptTokens,
        int completionTokens,
        long latencyMs
) {
}
