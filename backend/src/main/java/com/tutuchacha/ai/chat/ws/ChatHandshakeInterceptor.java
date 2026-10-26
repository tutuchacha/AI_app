package com.tutuchacha.ai.chat.ws;

import com.tutuchacha.ai.auth.JwtService;
import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * WS 握手鉴权：浏览器 WebSocket 不能带 Authorization 头，token 走查询参数。
 * 校验通过后把 username 放进 session attributes，后续帧不再重复解析。
 */
@Component
public class ChatHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;

    public ChatHandshakeInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = UriComponentsBuilder.fromUri(request.getURI())
                .build().getQueryParams().getFirst("token");
        if (!StringUtils.hasText(token)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            JwtService.TokenPayload payload = jwtService.parse(token);
            if (!StringUtils.hasText(payload.username())) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            attributes.put(ChatWebSocketHandler.USERNAME, payload.username());
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需处理
    }
}
