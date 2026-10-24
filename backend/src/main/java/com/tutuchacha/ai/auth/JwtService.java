package com.tutuchacha.ai.auth;

import com.tutuchacha.ai.config.JwtProperties;
import com.tutuchacha.ai.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 签发与校验：HS256，默认有效期 2h。
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration expiration;

    public JwtService(JwtProperties properties) {
        String secret = properties.secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.jwt.secret 至少需要 32 字节");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = properties.expiration() == null ? Duration.ofHours(2) : properties.expiration();
    }

    public String issue(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .claim("role", user.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key)
                .compact();
    }

    public TokenPayload parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Number uid = claims.get("uid", Number.class);
        return new TokenPayload(uid == null ? null : uid.longValue(), claims.getSubject(), claims.get("role", String.class));
    }

    public record TokenPayload(Long userId, String username, String role) {
    }
}
