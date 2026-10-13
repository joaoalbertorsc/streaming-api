CREATE TABLE watch_history_entry (
    id          UUID PRIMARY KEY,
    profile_id  UUID NOT NULL REFERENCES profile(id),
    movie_id    UUID NOT NULL REFERENCES movie(id),
    watched_at  TIMESTAMPTZ NOT NULL
);