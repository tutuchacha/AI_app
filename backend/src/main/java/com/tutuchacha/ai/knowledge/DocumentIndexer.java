package com.tutuchacha.ai.knowledge;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 解析调度：接口线程只负责落库 + 提交任务。
 * 单独成 bean 是为了让 @Async 走代理（同类内部调用不会异步）。
 */
@Component
public class DocumentIndexer {

    private final DocumentParser parser;

    public DocumentIndexer(DocumentParser parser) {
        this.parser = parser;
    }

    @Async("ragExecutor")
    public void submit(Long documentId) {
        parser.parse(documentId);
    }
}
