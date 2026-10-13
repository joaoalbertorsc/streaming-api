-- Enforces unique profile names per account, but only among active (non-trashed) profiles,
-- per the business rule agreed in the plan (section 2, invariant 7).
-- PostgreSQL-only: H2 does not support UNIQUE indexes with a WHERE clause, even in
-- MODE=PostgreSQL, so this lives in a separate location that only the real application
-- (never the test profile) includes. See ProfileRepositoryTest for the deferred test coverage.
CREATE UNIQUE INDEX uq_profile_account_name_active
    ON profile (account_id, name)
    WHERE deleted_at IS NULL;