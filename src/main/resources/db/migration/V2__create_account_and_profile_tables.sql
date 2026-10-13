CREATE TABLE account (
    id            UUID PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    pin_hash      VARCHAR(255) NOT NULL,
    plan          VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL
);

CREATE TABLE profile (
    id            UUID PRIMARY KEY,
    account_id    UUID         NOT NULL REFERENCES account(id),
    name          VARCHAR(255) NOT NULL,
    age_category  VARCHAR(20)  NOT NULL,
    owner         BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL
);