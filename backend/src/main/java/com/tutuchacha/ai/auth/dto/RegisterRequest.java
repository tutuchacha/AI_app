package com.tutuchacha.ai.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "^[A-Za-z0-9_]{3,32}$", message = "用户名需为 3-32 位字母、数字或下划线")
        String username,

        @NotBlank(message = "密码不能为空")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,32}$", message = "密码需 8-32 位且同时包含字母和数字")
        String password,

        @Size(max = 32, message = "昵称最长 32 位")
        String nickname
) {
}
