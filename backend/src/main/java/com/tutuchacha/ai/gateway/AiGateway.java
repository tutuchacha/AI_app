package com.tutuchacha.ai.gateway;

import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 统一模型入口：业务代码只依赖本接口，禁止直接 import Spring AI / 厂商 SDK。
 */
public interface AiGateway {

    /** 非流式补全，阻塞直到拿到完整回答。 */
    AiChatResponse complete(AiChatRequest request);

    /** 流式补全，逐帧返回增量文本；最后一帧可能只带 token 用量。 */
    Flux<AiStreamChunk> stream(AiChatRequest request);

    /** 文本向量：返回顺序与入参一致；批量大小由调用方按模型限制控制。 */
    List<float[]> embed(List<String> texts);

    /** 配置里的默认模型名，用于在首个事件里告诉前端。 */
    String defaultModel();
}
