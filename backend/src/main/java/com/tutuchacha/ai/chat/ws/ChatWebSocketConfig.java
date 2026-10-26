package com.tutuchacha.ai.chat.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * M1-07：/ws/chat 与 /api/chat/stream 共用 ChatOrchestrator 与 ChatEvent 结构。
 * 鉴权在握手拦截器里做（?token=JWT），所以 SecurityConfig 放行 /ws/**。
 */
@Configuration
@EnableWebSocket
public class ChatWebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler handler;
    private final ChatHandshakeInterceptor interceptor;

    public ChatWebSocketConfig(ChatWebSocketHandler handler, ChatHandshakeInterceptor interceptor) {
        this.handler = handler;
        this.interceptor = interceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/chat")
                .addInterceptors(interceptor)
                // 开发期放开 Origin；生产按域名收紧（M8）
                .setAllowedOriginPatterns("*");
    }
}
