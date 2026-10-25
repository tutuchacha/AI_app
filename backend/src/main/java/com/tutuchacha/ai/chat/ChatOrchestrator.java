package com.tutuchacha.ai.chat;

import com.tutuchacha.ai.chat.dto.ChatRequest;
import com.tutuchacha.ai.chat.dto.ChatResult;
import com.tutuchacha.ai.gateway.AiChatRequest;
import com.tutuchacha.ai.gateway.AiChatResponse;
import com.tutuchacha.ai.gateway.AiGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * 聊天唯一业务入口：M1-02 只做「拼提示词 + 调模型」，
 * 会话落库与流式在 M1-03/M1-05 接进来。
 */
@Service
public class ChatOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ChatOrchestrator.class);

    static final String SYSTEM_PROMPT = """
            你是「星屿 AI」的助手，风格温和、简洁。
            默认使用简体中文回答；不确定的内容要说明不确定，不要编造。
            """;

    private final AiGateway aiGateway;

    public ChatOrchestrator(AiGateway aiGateway) {
        this.aiGateway = aiGateway;
    }

    public ChatResult chat(String username, ChatRequest request) {
        String conversationId = StringUtils.hasText(request.conversationId())
                ? request.conversationId()
                : newConversationId();

        AiChatRequest aiRequest = AiChatRequest.of(SYSTEM_PROMPT, request.content().trim());
        AiChatResponse response = aiGateway.complete(aiRequest);

        log.info("chat user={} conversation={} model={} latencyMs={} tokens={}/{}",
                username, conversationId, response.model(), response.latencyMs(),
                response.promptTokens(), response.completionTokens());

        return new ChatResult(conversationId, response.content(), response.model(),
                response.promptTokens(), response.completionTokens(), response.latencyMs());
    }

    private String newConversationId() {
        return "c_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
