-- lin-spring-chat 初始 schema（MySQL 8.4, utf8mb4）
-- 时间统一 UTC 存储（应用层 LocalDateTime.now(ZoneOffset.UTC)），避免时区歧义

CREATE TABLE users (
    id            VARCHAR(36)  NOT NULL,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname      VARCHAR(50)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE conversations (
    id                VARCHAR(36)  NOT NULL,
    type              VARCHAR(20)  NOT NULL COMMENT 'DIRECT / GROUP',
    name              VARCHAR(100) NULL COMMENT '群名；DIRECT 为空',
    owner_id          VARCHAR(36)  NULL COMMENT '群主；DIRECT 为空',
    direct_user_min   VARCHAR(36)  NULL COMMENT '单聊成员对（按 id 排序）小端，唯一防重复建会话',
    direct_user_max   VARCHAR(36)  NULL,
    last_message_id   VARCHAR(36)  NULL,
    last_message_preview VARCHAR(400) NULL,
    last_message_at   DATETIME(6)  NULL,
    member_count      INT          NOT NULL DEFAULT 0,
    created_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_conv_direct_pair (direct_user_min, direct_user_max),
    KEY idx_conv_last_message (last_message_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE conversation_members (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    conversation_id      VARCHAR(36)  NOT NULL,
    user_id              VARCHAR(36)  NOT NULL,
    role                 VARCHAR(10)  NOT NULL COMMENT 'OWNER / MEMBER',
    joined_at            DATETIME(6)  NOT NULL,
    last_read_message_id VARCHAR(36)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_member (conversation_id, user_id),
    KEY idx_member_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE messages (
    id              VARCHAR(36)  NOT NULL,
    conversation_id VARCHAR(36)  NOT NULL,
    sender_id       VARCHAR(36)  NULL COMMENT 'SYSTEM 消息为操作发起者',
    type            VARCHAR(10)  NOT NULL COMMENT 'TEXT / SYSTEM',
    content         TEXT         NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    recalled        TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_msg_conv_time (conversation_id, created_at, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE undelivered_messages (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    user_id         VARCHAR(36) NOT NULL,
    message_id      VARCHAR(36) NOT NULL,
    conversation_id VARCHAR(36) NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_undelivered (user_id, message_id),
    KEY idx_undelivered_user (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
