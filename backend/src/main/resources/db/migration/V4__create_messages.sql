CREATE TABLE messages
(
    id      BIGINT       GENERATED ALWAYS AS IDENTITY,
    room_id BIGINT       NOT NULL,
    user_id BIGINT,
    content TEXT         NOT NULL,
    type    VARCHAR(20)  NOT NULL DEFAULT 'chat',
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_messages PRIMARY KEY (id),
    CONSTRAINT fk_messages_room FOREIGN KEY (room_id) REFERENCES rooms (id),
    CONSTRAINT fk_messages_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_messages_type CHECK (type IN ('chat', 'system'))
);

CREATE INDEX idx_messages_room_id_id ON messages (room_id, id DESC);
