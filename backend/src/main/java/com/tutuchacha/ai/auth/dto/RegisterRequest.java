package com.tutuchacha.ai.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "注册请求")
public record RegisterRequest(
        @Schema(description = "用户名，3-32 位字母/数字/下划线", example = "demo")
        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "^[A-Za-z0-9_]{3,32}$", message = "用户名需为 3-32 位字母、数字或下划线")
        String username,

        @Schema(description = "密码，8-32 位且同时包含字母和数字", example = "passw0rd")
        @NotBlank(message = "密码不能为空")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,32}$", message = "密码需 8-32 位且同时包含字母和数字")
        String password,

        @Schema(description = "昵称，可选，默认与用户名相同", example = "小屿")
        @Size(max = 32, message = "昵称最长 32 位")
        String nickname
) {
}
