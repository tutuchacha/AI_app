package com.tutuchacha.ai.date.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** 我的档案：全部可空，PUT 是整体覆盖（空字符串等于清空）。 */
@Schema(description = "恋爱助手：我的档案")
public record DateProfileRequest(
        @Size(max = 40, message = "昵称最多 40 字")
        @Schema(description = "昵称", example = "小林") String nickname,
        @Size(max = 16)
        @Schema(description = "性别（自填）", example = "男") String gender,
        @Min(value = 18, message = "年龄需 ≥ 18")
        @Max(value = 99, message = "年龄需 ≤ 99")
        @Schema(description = "年龄", example = "28") Integer age,
        @Size(max = 40)
        @Schema(description = "常驻城市，地点推荐会用它", example = "杭州") String city,
        @Size(max = 60)
        @Schema(description = "职业", example = "后端工程师") String occupation,
        @Size(max = 500)
        @Schema(description = "兴趣与日常", example = "跑步、咖啡、看展") String hobbies,
        @Size(max = 500)
        @Schema(description = "性格与沟通风格", example = "慢热，文字比当面自然") String personality,
        @Size(max = 500)
        @Schema(description = "期望的关系目标与节奏", example = "找长期关系，希望三个月内稳定") String goal,
        @Size(max = 1000)
        @Schema(description = "补充说明") String notes
) {
}
