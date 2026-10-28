package com.tutuchacha.ai.date.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 我的档案视图；未建档时接口返回 null，前端用空表单兜底。 */
@Schema(description = "恋爱助手：我的档案")
public record DateProfileView(
        @Schema(description = "档案 ID") Long id,
        String nickname,
        String gender,
        Integer age,
        String city,
        String occupation,
        String hobbies,
        String personality,
        String goal,
        String notes,
        @Schema(description = "更新时间") String updatedAt
) {
}
