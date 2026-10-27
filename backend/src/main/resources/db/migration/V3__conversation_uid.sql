-- M1-05 会话对外 ID：数据库主键保持 BIGINT，对外暴露 c_ 前缀的 uid
ALTER TABLE conversation
    ADD COLUMN IF NOT EXISTS uid VARCHAR(32) NOT NULL DEFAULT '';

COMMENT ON COLUMN conversation.uid IS '对外会话 ID（c_ 前缀）';

UPDATE conversation
SET uid = 'c_' || SUBSTRING(MD5(id::text || '-' || user_id::text), 1, 12)
WHERE uid = '';

ALTER TABLE conversation
    ADD CONSTRAINT uk_conv_uid UNIQUE (uid);
