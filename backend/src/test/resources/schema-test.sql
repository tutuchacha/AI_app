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

CREATE TABLE IF NOT EXISTS conversation
(
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    uid             VARCHAR(32) NOT NULL DEFAULT '',
    user_id         BIGINT      NOT NULL,
    module          VARCHAR(16) NOT NULL DEFAULT 'chat',
    persona_id      BIGINT      NULL,
    kb_id           BIGINT      NULL,
    title           VARCHAR(64) NOT NULL DEFAULT '',
    model_id        BIGINT      NULL,
    last_message_at TIMESTAMP   NULL,
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_conv_uid UNIQUE (uid)
);

CREATE TABLE IF NOT EXISTS message
(
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT      NOT NULL,
    role            VARCHAR(16) NOT NULL,
    content         TEXT        NOT NULL,
    tokens_in       INT         NOT NULL DEFAULT 0,
    tokens_out      INT         NOT NULL DEFAULT 0,
    latency_ms      INT         NOT NULL DEFAULT 0,
    model           VARCHAR(64) NOT NULL DEFAULT '',
    status          VARCHAR(16) NOT NULL DEFAULT 'DONE',
    request_id      VARCHAR(64) NULL,
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
