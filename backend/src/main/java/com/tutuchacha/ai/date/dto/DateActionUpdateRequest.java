package com.tutuchacha.ai.date.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** 回填执行状态与对方反应；只改传了的字段。 */
@Schema(description = "回填执行与反馈")
public record DateActionUpdateRequest(
        @Schema(description = "PLANNED / DONE / SKIPPED", example = "DONE") String status,
        @Schema(description = "UNKNOWN / GOOD / NEUTRAL / COLD / NO_REPLY", example = "GOOD") String result,
        @Size(max = 500, message = "备注最多 500 字")
        @Schema(description = "观察到的细节", example = "她主动补了营地名字") String note
) {
}
