CREATE TABLE movie (
    id                UUID PRIMARY KEY,
    title             VARCHAR(255)  NOT NULL,
    genre             VARCHAR(100)  NOT NULL,
    synopsis          VARCHAR(1000) NOT NULL,
    release_year      INTEGER       NOT NULL,
    cast_members      VARCHAR(1000) NOT NULL,
    duration_minutes  INTEGER       NOT NULL,
    classification    VARCHAR(20)   NOT NULL,
    minimum_plan      VARCHAR(20)   NOT NULL
);