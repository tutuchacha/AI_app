package com.tutuchacha.ai.gateway;

/**
 * 统一模型入口：业务代码只依赖本接口，禁止直接 import Spring AI / 厂商 SDK。
 * M1-03 会在这里增加流式方法（Flux&lt;String&gt; / ChatEvent）。
 */
public interface AiGateway {

    /**
     * 非流式补全，阻塞直到拿到完整回答。
     */
    AiChatResponse complete(AiChatRequest request);
}
