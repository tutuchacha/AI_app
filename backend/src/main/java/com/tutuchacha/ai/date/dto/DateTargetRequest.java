package com.tutuchacha.ai.date.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** 目标对象档案：name 必填，stage 缺省 MET。 */
@Schema(description = "恋爱助手：目标对象档案")
public record DateTargetRequest(
        @NotBlank(message = "对象称呼不能为空")
        @Size(max = 40, message = "称呼最多 40 字")
        @Schema(description = "称呼（昵称即可）", example = "小周") String name,
        @Size(max = 60)
        @Schema(description = "认识途径", example = "朋友介绍") String channel,
        @Schema(description = "MET / CHATTING / INVITED / DATING / TOGETHER / PAUSED", example = "CHATTING")
        String stage,
        @Min(value = 18, message = "年龄需 ≥ 18")
        @Max(value = 99, message = "年龄需 ≤ 99")
        @Schema(description = "年龄", example = "26") Integer age,
        @Size(max = 40)
        @Schema(description = "所在城市", example = "杭州") String city,
        @Size(max = 500)
        @Schema(description = "兴趣与在意的点", example = "喜欢露营、猫、日料") String interests,
        @Size(max = 500)
        @Schema(description = "雷点/忌讳", example = "不喜欢被追问前任") String taboos,
        @Size(max = 1000)
        @Schema(description = "其他备注：最近聊了什么、回消息频率") String notes,
        @Schema(description = "最近一次联系日期", example = "2026-10-26") LocalDate lastContactAt
) {
}
