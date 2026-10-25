package com.tutuchacha.ai.gateway;

import java.util.List;

/**
 * 一次模型调用的入参，业务层只依赖这个结构，不接触厂商 SDK。
 *
 * @param model         为空时用配置里的默认模型
 * @param messages      对话消息，按时间顺序
 * @param temperature   为空时用配置默认值
 * @param maxTokens     为空时用配置默认值
 */
public record AiChatRequest(
        String model,
        List<AiMessage> messages,
        Double temperature,
        Integer maxTokens
) {
    public AiChatRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public static AiChatRequest of(String systemPrompt, String userContent) {
        List<AiMessage> messages = systemPrompt == null || systemPrompt.isBlank()
                ? List.of(AiMessage.user(userContent))
                : List.of(AiMessage.system(systemPrompt), AiMessage.user(userContent));
        return new AiChatRequest(null, messages, null, null);
    }
}
