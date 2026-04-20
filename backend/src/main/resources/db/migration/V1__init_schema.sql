CREATE TABLE avatar_templates
(
    id        BIGINT       GENERATED ALWAYS AS IDENTITY,
    name      VARCHAR(100) NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    is_active BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_avatar_templates PRIMARY KEY (id)
);

CREATE TABLE users
(
    id             BIGINT       GENERATED ALWAYS AS IDENTITY,
    email          VARCHAR(255) NOT NULL,
    password_hash  VARCHAR(255),
    nickname       VARCHAR(50),
    avatar_id      BIGINT,
    provider       VARCHAR(20),
    provider_id    VARCHAR(255),
    role           VARCHAR(20)  NOT NULL DEFAULT 'USER',
    email_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    token_version  BIGINT       NOT NULL DEFAULT 0,
    withdrawn_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT fk_users_avatar FOREIGN KEY (avatar_id) REFERENCES avatar_templates (id) ON DELETE SET NULL
);

CREATE TABLE rooms
(
    id           BIGINT      GENERATED ALWAYS AS IDENTITY,
    invite_token UUID        NOT NULL DEFAULT gen_random_uuid(),
    name         VARCHAR(100) NOT NULL,
    owner_id     BIGINT       NOT NULL,
    is_private   BOOLEAN      NOT NULL DEFAULT FALSE,
    max_capacity INT          NOT NULL DEFAULT 10,
    status       VARCHAR(20)  NOT NULL DEFAULT 'active',
    deleted_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_rooms PRIMARY KEY (id),
    CONSTRAINT uk_rooms_invite_token UNIQUE (invite_token),
    CONSTRAINT fk_rooms_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT chk_rooms_status CHECK (status IN ('active', 'closed'))
);

CREATE INDEX idx_rooms_invite_token ON rooms (invite_token);

CREATE TABLE messages
(
    id         BIGINT      GENERATED ALWAYS AS IDENTITY,
    room_id    BIGINT      NOT NULL,
    user_id    BIGINT,
    content    TEXT        NOT NULL,
    type       VARCHAR(20) NOT NULL DEFAULT 'chat',
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_messages PRIMARY KEY (id),
    CONSTRAINT fk_messages_room FOREIGN KEY (room_id) REFERENCES rooms (id),
    CONSTRAINT fk_messages_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_messages_type CHECK (type IN ('chat', 'system'))
);

CREATE INDEX idx_messages_room_id_id ON messages (room_id, id DESC);
