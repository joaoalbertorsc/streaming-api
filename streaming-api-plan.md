# Streaming API Simulation — Planning Document

Practice project. Simulates a Netflix-like streaming service: one account (subscriber)
with multiple profiles, plans, content access rules, watch history, and reviews.

## 1. Overview and tech stack

- Java 21, Spring Boot (latest stable)
- Maven (`artifactId`: `streaming-api`, base package: `com.ebac.streaming`)
- Spring Web, Spring Security, Spring Data JPA
- PostgreSQL (run via Docker locally), Flyway for schema + seed data
- JWT authentication (stateless), BCrypt for password and PIN hashing
- Bean Validation (`jakarta.validation`) on all request DTOs
- Lombok on entities (`@Getter`, `@NoArgsConstructor`); DTOs as Java records
- springdoc-openapi (Swagger UI) with Bearer auth scheme
- JUnit 5 + Mockito; Testcontainers (PostgreSQL) for integration tests
- TDD: tests written before implementation, in the order described in section 6

### Scope decisions
- Movies only (no TV series) for this MVP.
- No admin/upload endpoints — the catalog is seeded via Flyway migrations at startup.
- No refresh token, no profile-switch token (Option B) — documented as future work.
- No PIN attempt limit/lockout — documented as future work.

---

## 2. Domain model

### Entities

| Entity | Fields |
|---|---|
| **Account** | `id` (UUID), `email` (unique), `passwordHash`, `pinHash`, `plan`, `createdAt` |
| **Profile** | `id` (UUID), `account`, `name`, `ageCategory`, `owner` (boolean), `deletedAt` (nullable), `createdAt` |
| **Movie** | `id` (UUID), `title`, `genre` (String), `synopsis`, `releaseYear`, `castMembers` (comma-separated String), `durationMinutes`, `classification`, `minimumPlan` |
| **WatchHistoryEntry** | `id` (UUID), `profile`, `movie`, `watchedAt` |
| **Review** | `id` (UUID), `profile`, `movie`, `stars` (1–5), `comment` (nullable), `createdAt`, `updatedAt` |

### Relationships
- Account 1 → N Profile
- Profile 1 → N WatchHistoryEntry, Profile 1 → N Review
- Movie 1 → N WatchHistoryEntry, Movie 1 → N Review
- Purging a trashed profile (see trash rules) cascades deletion of its history and reviews.

### Enums (with behavior, not just labels)

**Plan** — `BASIC` (maxProfiles=1), `STANDARD` (2), `PREMIUM` (4). Each has a tier level.
`plan.allows(minimumPlan)` compares tiers (e.g., `PREMIUM.allows(STANDARD)` → true).

**ContentRating** — `L`, `TEN`, `TWELVE`, `FOURTEEN`, `SIXTEEN`, `EIGHTEEN`. Each holds a minimum age.

**AgeCategory** — `CHILD` (allows `L` only), `TEEN` (allows up to `FOURTEEN`), `ADULT` (allows all).
`category.allows(rating)` holds the age rule.

### Business invariants
1. Account creation also creates the default profile: `ADULT`, `owner = true`. It can never be deleted or sent to the trash.
2. Quota: active profiles ≤ `plan.maxProfiles`. Trashed profiles don't count toward quota.
3. Access rule (content visibility and watch): a profile may see/watch a movie only if
   `account.plan.allows(movie.minimumPlan)` **and** `profile.ageCategory.allows(movie.classification)`.
4. Watch: requires an active profile owned by the authenticated account, and an allowed movie.
   If classification is `SIXTEEN`/`EIGHTEEN`, the account PIN (`X-Account-Pin` header) must also be
   validated before the watch proceeds. On success, saves a history entry and simulates playback
   (simple terminal messages: started / running / ended — no threads, no real video).
5. History: per profile, most recent first. Repeat viewings create separate entries (no merging/counting).
6. Review: only from a profile that has watched the movie; 1–5 stars, optional comment.
   One review per (profile, movie) — created via POST, changed via PUT.
7. Trash (soft delete): deleting a non-owner profile sets `deletedAt = now`. It can be restored within
   30 days, but restore fails if the plan's quota is full, or if the profile's name now conflicts with an
   active profile (enforced via a partial unique index on `Profile.name` where `deletedAt IS NULL`).
   A profile trashed for over 30 days is permanently purged by a daily scheduled job, cascading its
   history and reviews.
8. Plan migration:
   - Same-plan migration is rejected.
   - Upgrade: no profile list needed, changes the plan directly.
   - Downgrade: requires exactly `active profiles − new plan's maxProfiles` profile IDs (not the owner's,
     all active, all belonging to this account, no duplicates). Those profiles go to the trash and the
     plan changes, in a single transaction.
9. PIN: one PIN per account (not per profile). Required only when **watching** a `SIXTEEN`/`EIGHTEEN`
   movie, sent via `X-Account-Pin` header. Hashed with BCrypt. No other endpoint requires it. No attempt
   limit in this MVP (documented as future work).

---

## 3. Architecture

### Package structure (by feature)

```
com.ebac.streaming
├── auth        AuthController, AuthService, JwtService, JwtAuthenticationFilter,
│               AccountUserDetailsService, JwtProperties, RegisterRequest, LoginRequest, AuthResponse
├── account     Account, AccountRepository, AccountService
├── plan        Plan (enum), PlanMigrationController, PlanMigrationService,
│               PlanMigrationValidator, ProfileQuotaPolicy, PlanMigrationRequest, MigrationPreviewResponse
├── profile     Profile, AgeCategory (enum), ProfileRepository, ProfileService,
│               ProfileAccessService, ProfileController, TrashPurgeJob, DTOs
├── movie       Movie, ContentRating (enum), MovieRepository, MovieService,
│               ContentAccessPolicy, MovieController, MovieResponse, MovieSummaryResponse
├── watch       WatchHistoryEntry, WatchHistoryRepository, WatchService,
│               WatchController, WatchResponse, HistoryEntryResponse, PinPolicy
├── review      Review, ReviewRepository, ReviewService, ReviewController,
│               ReviewRequest, ReviewResponse
├── exception   GlobalExceptionHandler, ApiError, custom exceptions
└── config      SecurityConfig, OpenApiConfig, SchedulingConfig
```

### Layer responsibilities
- **Controller**: HTTP only. Validates (`@Valid`), delegates to one service method, returns the DTO.
- **Service**: orchestrates one use case; coordinates entities, repositories, and policy classes.
- **Entity**: owns its own simple invariants (`profile.moveToTrash()`, `profile.restore()`, `profile.isActive()`).
- **Policy/Validator**: one class per business rule, independently unit-testable.
- **Repository**: Spring Data interfaces; filtering logic lives in the query, not in-memory.

### Key classes

| Class | Responsibility |
|---|---|
| `ProfileAccessService` | Confirms a profile exists, is active, and belongs to the authenticated account. Reused by every profile-scoped endpoint. |
| `ProfileQuotaPolicy` | "Can this account have one more active profile?" Used by create and restore. |
| `ContentAccessPolicy` | Plan + age intersection rule. Used by movie listing and watch. |
| `PinPolicy` | Validates `X-Account-Pin` against the stored hash. Used only by `WatchService`, only for 16/18 content. |
| `PlanMigrationValidator` | All downgrade rules (count, ownership, active, not-owner, no duplicates). |
| `TrashPurgeJob` | Daily `@Scheduled` job purging profiles trashed over 30 days. |

### Design patterns

**Used**
- Repository pattern (Spring Data).
- DTOs as records with static factory mapping methods (e.g. `MovieResponse.from(movie)`).
- Policy/Validator objects for business rules (SRP-driven, not classic GoF).
- Enums with behavior for Plan/AgeCategory/ContentRating (Open/Closed — new values need no service changes).
- Static factory methods on entities for invariant-respecting creation (`Profile.createOwner(...)`).
- Filter chain (Spring Security JWT filter) and global exception handler (`@RestControllerAdvice`).

**Deliberately not used**
- Strategy per profile type/plan — the rules are data (ages, tiers), not differing algorithms; enums cover it.
- Factory class for users — only two kinds, and "owner" is just a boolean flag.
- Chain of Responsibility for validation — few checks, fixed order, a plain validator class is clearer.
- State pattern for trashed profiles — a nullable `deletedAt` is sufficient.
- Hexagonal architecture / CQRS — too heavy for this scope.

### Security model
- JWT subject = account ID only. Plan is **not** embedded in the token (it can change mid-session) —
  always read fresh from the database.
- `JwtAuthenticationFilter` validates the token and sets authentication. `/auth/**` and Swagger are public;
  everything else requires authentication.
- Single role: `SUBSCRIBER`. Management endpoints (profile CRUD, plan migration) require this role and
  operate at the account level (no `profileId` needed — the account is the owner of the action).
- Profile-scoped endpoints (movies, watch, history, reviews) take `profileId` as a request parameter and
  verify ownership via `ProfileAccessService`.
- PIN (`X-Account-Pin`) is required only on `POST /watch` when the movie's classification is 16 or 18.

**Known limitation (documented, not fixed in this MVP):** since all profiles share one JWT, the server
cannot verify which physical profile/device is actually in use — only that the `profileId` belongs to the
authenticated account. A second, profile-scoped token (entered via a profile PIN, "Option B") would close
this gap and is listed as a future improvement.

---

## 4. API contract

Base path: `/api/v1`. Auth: `Authorization: Bearer <jwt>` on all but `/auth/**` and Swagger.
Errors use one `ApiError` shape: `timestamp`, `status`, `error`, `message`, `path`.

### Auth
| Method & Path | Request | Response | Notes |
|---|---|---|---|
| `POST /auth/register` | `email`, `password` (`min=8`), `pin` (`\d{4,6}`), `defaultProfileName` | 201 `AuthResponse(token, accountId, defaultProfileId)` | Creates account (Basic plan) + owner profile. 409 if email exists. |
| `POST /auth/login` | `email`, `password` | 200 `AuthResponse(token, accountId)` | 401 on bad credentials. |

### Profiles (SUBSCRIBER role, account-level)
| Method & Path | Request | Response | Notes |
|---|---|---|---|
| `POST /profiles` | `name`, `ageCategory` | 201 `ProfileResponse` | 409 if quota exceeded. |
| `GET /profiles` | — | 200 `List<ProfileResponse>` | Active profiles only. |
| `DELETE /profiles/{profileId}` | — | 204 | 400 if owner profile. |
| `GET /profiles/trash` | — | 200 `List<TrashedProfileResponse>` | Within 30-day window. |
| `POST /profiles/{profileId}/restore` | — | 200 `ProfileResponse` | 409 on quota full or name conflict. |

### Plan migration (SUBSCRIBER role, account-level)
| Method & Path | Request | Response | Notes |
|---|---|---|---|
| `GET /plan/migration-preview?targetPlan=` | — | 200 `MigrationPreviewResponse(currentPlan, targetPlan, profilesToRemove, eligibleProfiles)` | For client-side selection UI. |
| `POST /plan/migration` | `PlanMigrationRequest(targetPlan, profileIdsToRemove)` | 200 `AccountResponse` | 409 on any validation failure. |

### Movies (profile-scoped)
| Method & Path | Request | Response | Notes |
|---|---|---|---|
| `GET /movies?profileId=` | — | 200 `List<MovieSummaryResponse>` | Filtered by plan + age in the query. |
| `GET /movies/{movieId}?profileId=` | — | 200 `MovieResponse` | 403/404 on access/ownership failure. |

### Watch (profile-scoped)
| Method & Path | Request | Response | Notes |
|---|---|---|---|
| `POST /watch?profileId=&movieId=` | Header `X-Account-Pin` (required only for 16/18) | 200 `WatchResponse(movieTitle, message, watchedAt)` | Saves history entry on success; PIN checked before saving. |

### History (profile-scoped)
| Method & Path | Request | Response | Notes |
|---|---|---|---|
| `GET /history?profileId=` | — | 200 `List<HistoryEntryResponse(movieTitle, watchedAt)>` | Ordered `watchedAt desc`. Repeats = separate entries. |

### Reviews (profile-scoped)
| Method & Path | Request | Response | Notes |
|---|---|---|---|
| `POST /reviews?profileId=` | `movieId`, `stars` (1–5), `comment?` | 201 `ReviewResponse` | 409 if never watched or already reviewed. |
| `PUT /reviews/{reviewId}?profileId=` | `stars`, `comment?` | 200 `ReviewResponse` | 403/404 if not owned by this profile. |
| `GET /reviews?profileId=` | — | 200 `List<ReviewResponse>` | Profile's own reviews. |

### Status codes
`200` OK · `201` created · `204` deleted · `400` invalid input/invariant · `401` auth failure ·
`403` forbidden (ownership/access/PIN) · `404` not found · `409` conflict (quota, duplicates, invalid migration, review-without-watch).

---

## 5. TDD checklist

Tests are grouped by layer, in implementation order: pure domain first, then policies, then services
(mocked repositories), then controllers (`@WebMvcTest`), then repository tests, then a thin end-to-end slice.

### Enums
- `Plan`: `maxProfiles()` per tier; `allows()` tier comparisons (e.g. `BASIC.allows(STANDARD)` false).
- `AgeCategory`: `allows()` per rating (e.g. `CHILD.allows(TEN)` false, `TEEN.allows(FOURTEEN)` true).

### Entities
- `Profile.createOwner()` → owner=true, ADULT, not trashed.
- `Profile.moveToTrash()` sets `deletedAt`; throws for owner.
- `Profile.restore()` clears `deletedAt`.
- `Profile.isActive()` reflects `deletedAt` state.
- `Account` defaults to `BASIC` plan on creation.

### Policies
- `ProfileQuotaPolicy`: allows under quota, rejects at quota, trashed profiles excluded from count.
- `ContentAccessPolicy`: allow/reject matrix across plan-pass/age-pass combinations.
- `PlanMigrationValidator`: same-plan rejected; upgrade requires empty list; downgrade exact/too-few/too-many counts; owner-in-list rejected; foreign ID rejected; trashed ID rejected; duplicates rejected.
- `PinPolicy`: correct PIN passes; wrong PIN throws; comparison uses BCrypt matching.

### Services (mocked repositories)
- `AuthService`: register hashes password+PIN, creates owner profile atomically, rejects duplicate email; login succeeds/fails correctly without leaking which field was wrong.
- `ProfileService`: create delegates to quota policy; delete rejects for owner; restore respects quota and name-conflict rules; list excludes trashed.
- `PlanMigrationService`: validator failure → no persistence (rollback); success → profiles trashed + plan changed atomically; upgrade has no profile side effects.
- `MovieService`: listing honors combined plan+age filter (verified via repository query, see repository tests).
- `WatchService`: rejects foreign/trashed profile; rejects blocked access with no history write; 16/18 without/with wrong PIN rejects with no write; 16/18 with correct PIN succeeds; ≤14 needs no PIN; successful watch writes exactly one entry; repeat watch writes a second entry.
- `HistoryService`: ordered `watchedAt desc`; scoped strictly to the requested profile.
- `ReviewService`: create rejects if never watched; create rejects duplicate (must use update); update rejects if not owned; update changes fields + `updatedAt`; stars range guarded at service level too.
- `TrashPurgeJob`: purges >30 days; leaves <30 days untouched; cascades history/review deletion.

### Controllers (`@WebMvcTest`)
Per endpoint: happy path status+shape; missing/invalid token → 401; `@Valid` violations → 400; service
rejections mapped to correct status via `GlobalExceptionHandler`; foreign `profileId` → 403 on all
profile-scoped endpoints.

### Repository tests (`@DataJpaTest` / Testcontainers)
- `MovieRepository` custom query across plan/classification combinations.
- Partial unique index on `Profile.name` (active-only) — allows duplicate active/trashed pair, rejects duplicate active/active.
- `WatchHistoryRepository` ordering by `watchedAt desc` per profile.

### End-to-end slice (`@SpringBootTest` + Testcontainers + Flyway)
- Register → create 3 profiles → downgrade to Standard with correct IDs → 2 trashed, plan changed.
- Register → watch allowed movie → check history → watch again → 2 entries, most recent first.
- Register → watch 18-rated movie without PIN → 403 → retry with correct PIN → success → review → update review.
- Restore after quota freed → succeeds; restore while quota full → 409.

---

## 6. Future improvements (explicitly out of MVP scope)
- Profile-scoped token ("Option B"): entering a profile with a PIN issues a short-lived token carrying
  `accountId` + `profileId`, closing the "any profileId can be sent" limitation described in section 3.
- PIN attempt limit and temporary lockout (e.g. 5 attempts → 15-minute lock).
- JWT refresh token (current MVP uses a single 1-hour token, configurable in `application.yml`).
- TV series support alongside movies.
- Per-profile optional PINs (currently a single account-level PIN).
- Endpoint to change the account PIN (requiring the current PIN).
- Migration to Spring Boot 4.

---

## 7. Implementation decisions (made during development)

Decisions taken after this plan was written. Where they differ from earlier sections, this section wins.

- **Spring Boot 3.5.x.** A bump to 4.1.1 broke 42 tests (Jackson 3, Flyway auto-config, springdoc 2.x
  incompatibility). Reverted to 3.5.0; migrating to Boot 4 is a separate future task.
- **Test databases.** Unit and `@WebMvcTest` tests need no database. `MovieRepositoryTest` and
  `WatchHistoryRepositoryTest` use H2 in `MODE=PostgreSQL` (`test` profile, `db/testmigration` adds an
  H2 compatibility script). `ProfileRepositoryTest` and `StreamingApiIntegrationTest` run on a real
  PostgreSQL via Testcontainers, because the partial unique index is PostgreSQL-only.
- **PostgreSQL-only migrations** live in `db/migration-postgres` (V6, partial unique index on
  `profile(account_id, name) WHERE deleted_at IS NULL`), which the H2 test profile never loads.
- **Catalog seed.** `V7__seed_movies.sql` loads 20 invented movies with fixed UUIDs
  (`10000000-0000-0000-0000-0000000000NN`) covering the plan × classification matrix, so tests can
  reference specific movies by ID.
- **Foreign or trashed `profileId` returns 404** (`ProfileNotFoundException`), not 403, on
  profile-scoped endpoints: the API does not reveal whether a profile exists on another account.
- **History response** is `HistoryEntryResponse(movieId, movieTitle, watchedAt)`, served by a dedicated
  `HistoryController` at `GET /api/v1/history`.
- **Configuration.** Local PostgreSQL runs from `docker-compose.yml`. DB credentials and the JWT secret
  come from environment variables, with a git-ignored `.env` imported via `spring.config.import`. The
  JWT secret has no default in `application.yml`. Tests get theirs from
  `src/test/resources/application.properties`.
- **Trash retention is enforced on read**, not only by the nightly purge: `GET /profiles/trash` lists only
  profiles trashed less than `Profile.TRASH_RETENTION` (30 days) ago, and restoring an older one returns
  404 as if it were already purged.
- **PIN change endpoint** (planned as "requires the current PIN") is not implemented; it moves to
  future improvements.
