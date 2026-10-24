package com.tutuchacha.ai.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tutuchacha.ai.auth.dto.AuthResponse;
import com.tutuchacha.ai.auth.dto.LoginRequest;
import com.tutuchacha.ai.auth.dto.RegisterRequest;
import com.tutuchacha.ai.auth.dto.UserProfile;
import com.tutuchacha.ai.common.BusinessException;
import com.tutuchacha.ai.common.ErrorCode;
import com.tutuchacha.ai.user.User;
import com.tutuchacha.ai.user.UserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserProfile register(RegisterRequest request) {
        if (findByUsername(request.username()) != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已存在");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(StringUtils.hasText(request.nickname()) ? request.nickname() : request.username());
        user.setRole("USER");
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已存在");
        }
        return toProfile(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = findByUsername(request.username());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        return new AuthResponse(jwtService.issue(user), user.getUsername(), user.getNickname());
    }

    public UserProfile me(String username) {
        User user = findByUsername(username);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在或已失效");
        }
        return toProfile(user);
    }

    private User findByUsername(String username) {
        return userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
    }

    private UserProfile toProfile(User user) {
        return new UserProfile(user.getId(), user.getUsername(), user.getNickname(), user.getRole());
    }
}
