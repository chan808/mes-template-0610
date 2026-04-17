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
