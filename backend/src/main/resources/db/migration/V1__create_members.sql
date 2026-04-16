CREATE TABLE members
(
    id             BIGINT       GENERATED ALWAYS AS IDENTITY,
    email          VARCHAR(255) NOT NULL,
    password       VARCHAR(255),
    role           VARCHAR(20)  NOT NULL DEFAULT 'USER',
    email_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    provider       VARCHAR(20),
    provider_id    VARCHAR(255),
    provider_key   VARCHAR(280) GENERATED ALWAYS AS (
        CASE WHEN provider IS NOT NULL THEN provider || ':' || provider_id END
    ) STORED,
    nickname       VARCHAR(50),
    withdrawn_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_members PRIMARY KEY (id),
    CONSTRAINT uk_members_email UNIQUE (email),
    CONSTRAINT uk_members_provider_key UNIQUE (provider_key)
);

CREATE INDEX idx_members_withdrawn_at ON members(withdrawn_at);
