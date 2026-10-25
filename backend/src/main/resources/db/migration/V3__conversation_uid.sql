-- M1-05 会话对外 ID：数据库主键保持 BIGINT，对外暴露 c_ 前缀的 uid
ALTER TABLE conversation
    ADD COLUMN uid VARCHAR(32) NOT NULL DEFAULT '' COMMENT '对外会话 ID（c_ 前缀）' AFTER id;

UPDATE conversation
SET uid = CONCAT('c_', SUBSTRING(MD5(CONCAT(id, '-', user_id)), 1, 12))
WHERE uid = '';

ALTER TABLE conversation
    ADD UNIQUE KEY uk_conv_uid (uid);
