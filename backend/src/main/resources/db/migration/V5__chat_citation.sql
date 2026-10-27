-- M2-05 回答引用的知识库片段（JSON 数组，刷新后可恢复）
ALTER TABLE message
    ADD COLUMN citation_json MEDIUMTEXT NULL COMMENT '引用的知识库片段 JSON，无引用为空' AFTER request_id;
