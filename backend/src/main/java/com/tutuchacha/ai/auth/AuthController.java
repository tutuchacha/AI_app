package com.tutuchacha.ai.auth;

import com.tutuchacha.ai.auth.dto.AuthResponse;
import com.tutuchacha.ai.auth.dto.LoginRequest;
import com.tutuchacha.ai.auth.dto.RegisterRequest;
import com.tutuchacha.ai.auth.dto.UserProfile;
import com.tutuchacha.ai.common.Result;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Result<UserProfile> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(authService.register(request));
    }

    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @GetMapping("/me")
    public Result<UserProfile> me(Authentication authentication) {
        return Result.ok(authService.me(authentication.getName()));
    }
}
