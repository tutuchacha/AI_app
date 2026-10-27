package com.tutuchacha.ai.knowledge;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.util.Locale;

/** 文档上传与解析配置：落盘目录、单文件上限、是否异步解析、向量检索实现。 */
@ConfigurationProperties(prefix = "app.rag")
public record RagProperties(
        String uploadDir,
        DataSize maxFileSize,
        Boolean async,
        Integer embeddingBatchSize,
        String vectorStore,
        Integer topK,
        Double minScore
) {

    public String uploadDirOrDefault() {
        return uploadDir == null || uploadDir.isBlank() ? "./uploads" : uploadDir.trim();
    }

    public long maxFileSizeBytes() {
        return maxFileSize == null ? 20L * 1024 * 1024 : maxFileSize.toBytes();
    }

    /** 默认异步；测试里关掉以便断言解析结果。 */
    public boolean asyncEnabled() {
        return async == null || async;
    }

    /** 单次向量请求的切片数，百炼 text-embedding-v3 单批上限 10。 */
    public int embeddingBatchSizeOrDefault() {
        return embeddingBatchSize == null || embeddingBatchSize < 1 ? 10 : embeddingBatchSize;
    }

    /** 向量检索实现：pg = pgvector，memory = Java 侧余弦（H2 测试）。 */
    public String vectorStoreOrDefault() {
        return vectorStore == null || vectorStore.isBlank()
                ? "pg" : vectorStore.trim().toLowerCase(Locale.ROOT);
    }

    /** 检索默认返回片数。 */
    public int topKOrDefault() {
        return topK == null || topK < 1 ? 6 : Math.min(topK, 20);
    }

    /** 检索默认相似度下限，低于该值的切片直接丢弃。 */
    public double minScoreOrDefault() {
        return minScore == null ? 0.2d : minScore;
    }
}
