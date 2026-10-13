CREATE TABLE review (
    id          UUID PRIMARY KEY,
    profile_id  UUID NOT NULL REFERENCES profile(id),
    movie_id    UUID NOT NULL REFERENCES movie(id),
    stars       INTEGER NOT NULL,
    comment     VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uq_review_profile_movie ON review (profile_id, movie_id);