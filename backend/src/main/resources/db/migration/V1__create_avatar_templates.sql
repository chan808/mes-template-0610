CREATE TABLE avatar_templates
(
    id        BIGINT       GENERATED ALWAYS AS IDENTITY,
    name      VARCHAR(100) NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    is_active BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_avatar_templates PRIMARY KEY (id)
);
