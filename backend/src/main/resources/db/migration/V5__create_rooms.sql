CREATE TABLE rooms
(
    id                      BIGINT       GENERATED ALWAYS AS IDENTITY,
    invite_token            UUID         NOT NULL DEFAULT gen_random_uuid(),
    invite_token_expires_at TIMESTAMPTZ,
    name                    VARCHAR(100) NOT NULL,
    owner_id                BIGINT       NOT NULL,
    is_private              BOOLEAN      NOT NULL DEFAULT FALSE,
    max_capacity            INT          NOT NULL DEFAULT 10,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'active',
    deleted_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL,
    updated_at              TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_rooms PRIMARY KEY (id),
    CONSTRAINT uk_rooms_invite_token UNIQUE (invite_token),
    CONSTRAINT fk_rooms_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT chk_rooms_status CHECK (status IN ('active', 'closed'))
);

CREATE INDEX idx_rooms_invite_token ON rooms(invite_token);
