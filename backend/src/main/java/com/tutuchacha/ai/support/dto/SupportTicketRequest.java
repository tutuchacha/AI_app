package com.tutuchacha.ai.support.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 提交转人工工单：问题必填，其余是兜底现场信息，便于人工复现。 */
@Schema(description = "转人工工单提交")
public record SupportTicketRequest(
        @NotBlank(message = "问题不能为空")
        @Size(max = 500, message = "问题最多 500 字")
        @Schema(description = "用户原问题", example = "退款多久到账") String question,
        @Schema(description = "触发兜底的会话 ID", example = "c_1a2b3c") String conversationUid,
        @Schema(description = "当时选中的知识库 ID", example = "3") Long kbId,
        @DecimalMin(value = "0", message = "引用分不能为负")
        @DecimalMax(value = "1", message = "引用分不能超过 1")
        @Schema(description = "触发时的最高引用分", example = "0.51") Double bestScore
) {
}
