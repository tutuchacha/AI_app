package com.tutuchacha.ai.analysis;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 文档分析配置：是否异步、拼接正文上限与输出 token 上限。 */
@ConfigurationProperties(prefix = "app.analysis")
public record AnalysisProperties(
        Boolean async,
        Integer maxChars,
        Integer maxChunks,
        Integer maxTokens
) {

    /** 默认异步；测试里关掉以便同步断言结果。 */
    public boolean asyncEnabled() {
        return async == null || async;
    }

    /** 拼进提示词的正文上限，超出截断并标注。 */
    public int maxCharsOrDefault() {
        return maxChars == null || maxChars < 1000 ? 12000 : maxChars;
    }

    /** 最多读取的切片数，防止长文档一次拉太多。 */
    public int maxChunksOrDefault() {
        return maxChunks == null || maxChunks < 1 ? 60 : maxChunks;
    }

    /** 单次分析输出上限，摘要/结构/追问都不需要太长。 */
    public int maxTokensOrDefault() {
        return maxTokens == null || maxTokens < 256 ? 2000 : maxTokens;
    }
}
