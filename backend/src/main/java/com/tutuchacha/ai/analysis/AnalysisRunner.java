package com.tutuchacha.ai.analysis;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 分析调度：接口线程只负责落库 + 提交任务。
 * 单独成 bean 是为了让 @Async 走代理（同类内部调用不会异步）。
 */
@Component
public class AnalysisRunner {

    private final AnalysisWorker worker;

    public AnalysisRunner(AnalysisWorker worker) {
        this.worker = worker;
    }

    @Async("analysisExecutor")
    public void submit(Long analysisId) {
        worker.run(analysisId);
    }
}
