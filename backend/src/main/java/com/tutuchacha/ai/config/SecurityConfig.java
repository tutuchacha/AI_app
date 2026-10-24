package com.tutuchacha.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutuchacha.ai.auth.JwtAuthFilter;
import com.tutuchacha.ai.auth.JwtService;
import com.tutuchacha.ai.common.ErrorCode;
import com.tutuchacha.ai.common.Result;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * M0-B04 起：仅注册/登录、健康检查与文档公开，其余需 Bearer token。
 * 未登录/无权限也返回统一 JSON，避免前端收到空 body。
 */
@Configuration
public class SecurityConfig {

    private final ObjectMapper objectMapper;
    private final JwtService jwtService;

    public SecurityConfig(ObjectMapper objectMapper, JwtService jwtService) {
        this.objectMapper = objectMapper;
        this.jwtService = jwtService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint((request, response, ex) ->
                                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, Result.fail(ErrorCode.UNAUTHORIZED)))
                        .accessDeniedHandler((request, response, ex) ->
                                writeJson(response, HttpServletResponse.SC_FORBIDDEN, Result.fail(ErrorCode.FORBIDDEN))))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private void writeJson(HttpServletResponse response, int status, Result<Void> body) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
