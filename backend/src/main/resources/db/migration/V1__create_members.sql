CREATE TABLE members
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    email        VARCHAR(255) NOT NULL,
    password     VARCHAR(255) NULL,
    role         VARCHAR(20)  NOT NULL DEFAULT 'USER',
    email_verified TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Email verification status',
    provider     VARCHAR(20)  NULL COMMENT 'OAuth provider (GOOGLE/NAVER/KAKAO)',
    provider_id  VARCHAR(255) NULL COMMENT 'Provider user identifier',
    provider_key VARCHAR(280) GENERATED ALWAYS AS (
        IF(provider IS NOT NULL, CONCAT(provider, ':', provider_id), NULL)
    ) STORED COMMENT 'Composite unique key for provider login',
    nickname     VARCHAR(50) NULL COMMENT 'Optional nickname',
    withdrawn_at DATETIME(6)  NULL COMMENT 'Soft delete timestamp for withdrawn members',
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_members_email (email),
    UNIQUE KEY uk_members_provider_key (provider_key),
    KEY idx_members_withdrawn_at (withdrawn_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
