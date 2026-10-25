-- M1-01 会话与消息表
CREATE TABLE IF NOT EXISTS conversation
(
    id              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id         BIGINT      NOT NULL COMMENT '所属用户 sys_user.id',
    module          VARCHAR(16) NOT NULL DEFAULT 'chat' COMMENT '业务模块：chat / companion / support / analysis',
    persona_id      BIGINT      NULL COMMENT '角色卡 ID（companion 模块使用）',
    kb_id           BIGINT      NULL COMMENT '知识库 ID（knowledge 模块使用）',
    title           VARCHAR(64) NOT NULL DEFAULT '' COMMENT '会话标题，首条用户消息截断 30 字',
    model_id        BIGINT      NULL COMMENT '模型 ID（M1-02 模型表接入前可为空）',
    last_message_at DATETIME    NULL COMMENT '最后一条消息时间，用于列表排序',
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_conv_user_module (user_id, module, last_message_at),
    CONSTRAINT fk_conv_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='会话表';

CREATE TABLE IF NOT EXISTS message
(
    id              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    conversation_id BIGINT      NOT NULL COMMENT '所属会话 conversation.id',
    role            VARCHAR(16) NOT NULL COMMENT '角色：user / assistant / system',
    content         MEDIUMTEXT  NOT NULL COMMENT '消息内容，流式结束后为完整文本',
    tokens_in       INT         NOT NULL DEFAULT 0 COMMENT '输入 token 数',
    tokens_out      INT         NOT NULL DEFAULT 0 COMMENT '输出 token 数',
    latency_ms      INT         NOT NULL DEFAULT 0 COMMENT '首字或整体耗时（毫秒）',
    model           VARCHAR(64) NOT NULL DEFAULT '' COMMENT '实际调用的模型名，如 qwen-plus',
    status          VARCHAR(16) NOT NULL DEFAULT 'DONE' COMMENT 'STREAMING / DONE / STOPPED / FAILED',
    request_id      VARCHAR(64) NULL COMMENT '本次生成请求 ID，便于重试与日志关联',
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_msg_conv (conversation_id, id),
    CONSTRAINT fk_msg_conv FOREIGN KEY (conversation_id) REFERENCES conversation (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='消息表';
