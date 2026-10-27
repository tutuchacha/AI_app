-- M3-01 文档分析任务：摘要 / 结构抽取 / 追问
-- 结果直接存 Markdown，前端用轻量渲染；按文档删记录走外键级联。
CREATE TABLE IF NOT EXISTS document_analysis
(
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    document_id       BIGINT       NOT NULL COMMENT '所属文档 document.id',
    kb_id             BIGINT       NOT NULL COMMENT '所属知识库，便于按库统计',
    analysis_type     VARCHAR(16)  NOT NULL COMMENT 'SUMMARY / OUTLINE / QUESTIONS',
    status            VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / RUNNING / READY / FAILED',
    content           MEDIUMTEXT   NULL COMMENT 'Markdown 结果',
    model             VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '实际使用的模型',
    prompt_tokens     INT          NOT NULL DEFAULT 0 COMMENT '输入 token',
    completion_tokens INT          NOT NULL DEFAULT 0 COMMENT '输出 token',
    latency_ms        INT          NOT NULL DEFAULT 0 COMMENT '模型耗时（毫秒）',
    error_msg         VARCHAR(500) NOT NULL DEFAULT '' COMMENT '失败原因（截断 500 字）',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_analysis_doc (document_id, id),
    KEY idx_analysis_doc_type (document_id, analysis_type, id),
    CONSTRAINT fk_analysis_doc FOREIGN KEY (document_id) REFERENCES document (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='文档分析任务';
