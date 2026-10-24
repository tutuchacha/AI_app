package com.tutuchacha.ai.auth;

import com.tutuchacha.ai.auth.dto.AuthResponse;
import com.tutuchacha.ai.auth.dto.LoginRequest;
import com.tutuchacha.ai.auth.dto.RegisterRequest;
import com.tutuchacha.ai.auth.dto.UserProfile;
import com.tutuchacha.ai.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "鉴权", description = "注册、登录、当前用户")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "注册", description = "用户名 3-32 位；密码 8-32 位且含字母和数字")
    @SecurityRequirements
    @PostMapping("/register")
    public Result<UserProfile> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(authService.register(request));
    }

    @Operation(summary = "登录", description = "成功返回 JWT，有效期 2 小时")
    @SecurityRequirements
    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @Operation(summary = "当前用户", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/me")
    public Result<UserProfile> me(Authentication authentication) {
        return Result.ok(authService.me(authentication.getName()));
    }
}
