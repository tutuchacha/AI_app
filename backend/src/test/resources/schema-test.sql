CREATE TABLE IF NOT EXISTS sys_user
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(32)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname      VARCHAR(32)  NOT NULL DEFAULT '',
    role          VARCHAR(16)  NOT NULL DEFAULT 'USER',
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_username UNIQUE (username)
);
