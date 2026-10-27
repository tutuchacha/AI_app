-- M2-05 回答引用的知识库片段（JSON 数组，刷新后可恢复）
ALTER TABLE message
    ADD COLUMN IF NOT EXISTS citation_json TEXT NULL;

COMMENT ON COLUMN message.citation_json IS '引用的知识库片段 JSON，无引用为空';
