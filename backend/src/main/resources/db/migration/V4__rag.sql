-- M2-01 RAG 知识库与文档表
-- 向量先存 JSON（MySQL 8 无原生 VECTOR），检索在 Java 侧算相似度；小规模够用，M6 再换向量库。
CREATE TABLE IF NOT EXISTS knowledge_base
(
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id         BIGINT       NOT NULL COMMENT '所属用户 sys_user.id',
    name            VARCHAR(64)  NOT NULL COMMENT '知识库名称',
    description     VARCHAR(255) NOT NULL DEFAULT '' COMMENT '描述',
    embedding_model VARCHAR(64)  NOT NULL DEFAULT 'text-embedding-v3' COMMENT '向量模型，变更需重建索引',
    chunk_size      INT          NOT NULL DEFAULT 800 COMMENT '切片字符数',
    chunk_overlap   INT          NOT NULL DEFAULT 120 COMMENT '切片重叠字符数',
    document_count  INT          NOT NULL DEFAULT 0 COMMENT '文档数（冗余统计，随文档增删维护）',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_kb_user (user_id, id),
    CONSTRAINT fk_kb_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='知识库';

CREATE TABLE IF NOT EXISTS document
(
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    kb_id       BIGINT       NOT NULL COMMENT '所属知识库 knowledge_base.id',
    filename    VARCHAR(255) NOT NULL COMMENT '原始文件名',
    ext         VARCHAR(16)  NOT NULL DEFAULT '' COMMENT '扩展名：pdf / docx / txt / md',
    size        BIGINT       NOT NULL DEFAULT 0 COMMENT '文件字节数',
    sha256      CHAR(64)     NOT NULL DEFAULT '' COMMENT '内容摘要，同库去重用',
    status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / PARSING / INDEXING / READY / FAILED',
    chunk_count INT          NOT NULL DEFAULT 0 COMMENT '切片数',
    error_msg   VARCHAR(500) NOT NULL DEFAULT '' COMMENT '失败原因（截断 500 字）',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_doc_kb (kb_id, id),
    KEY idx_doc_sha (kb_id, sha256),
    CONSTRAINT fk_doc_kb FOREIGN KEY (kb_id) REFERENCES knowledge_base (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='知识库文档';

CREATE TABLE IF NOT EXISTS document_chunk
(
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    document_id   BIGINT      NOT NULL COMMENT '所属文档 document.id',
    kb_id         BIGINT      NOT NULL COMMENT '所属知识库，检索按库过滤',
    chunk_index   INT         NOT NULL COMMENT '文档内顺序，从 0 开始',
    content       MEDIUMTEXT  NOT NULL COMMENT '切片文本',
    embedding     JSON        NULL COMMENT '向量（JSON 数组），未索引时为 NULL',
    token_count   INT         NOT NULL DEFAULT 0 COMMENT '估算 token 数',
    metadata_json JSON        NULL COMMENT '页码、标题等来源信息',
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_chunk_kb (kb_id, document_id),
    KEY idx_chunk_doc (document_id, chunk_index),
    CONSTRAINT fk_chunk_doc FOREIGN KEY (document_id) REFERENCES document (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='文档切片';
