package com.tutuchacha.ai.knowledge;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/** 文档上传与解析配置：落盘目录、单文件上限、是否异步解析。 */
@ConfigurationProperties(prefix = "app.rag")
public record RagProperties(
        String uploadDir,
        DataSize maxFileSize,
        Boolean async
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
}
