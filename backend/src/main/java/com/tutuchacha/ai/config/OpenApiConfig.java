package com.tutuchacha.ai.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "星屿 AI API",
                version = "v0.1",
                description = "M0 骨架：统一响应、鉴权、健康检查。在线调试：/swagger-ui.html"))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "登录接口返回的 token，填在这里即可调试 /api/auth/me")
public class OpenApiConfig {
}
