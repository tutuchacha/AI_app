package com.tutuchacha.ai.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "登录结果")
public record AuthResponse(
        @Schema(description = "JWT，放到 Authorization: Bearer 后面")
        String token,

        @Schema(description = "用户名")
        String username,

        @Schema(description = "昵称")
        String nickname
) {
}
