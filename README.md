# streaming-api

🇺🇸 English | [🇧🇷 Português (Brasil)](README.pt-BR.md)

Practice project — streaming API simulation (accounts, profiles, plans, age/plan-gated catalog,
watch history, reviews). See `streaming-api-plan.md` for the full domain model, architecture,
API contract and TDD checklist.

## Requirements

- JDK 21
- Maven 3.9+
- Docker (local PostgreSQL and Testcontainers-based tests)

## Local setup

1. Create your local settings file (DB credentials and JWT secret). It is git-ignored:

   ```bash
   cp .env.example .env
   ```

   Set `JWT_SECRET` to a random string of at least 32 characters (`openssl rand -base64 48`).
   The application reads `.env` automatically; real environment variables take precedence.

2. Start PostgreSQL:

   ```bash
   docker compose up -d
   ```

   This starts `streaming-postgres` (PostgreSQL 16) on port 5432, database `streaming`, with a
   persistent volume. Connect with DBeaver using the credentials from `.env`.

3. Run the application (or run `StreamingApiApplication` from the IDE, with the project root as
   working directory so `.env` is found):

   ```bash
   mvn spring-boot:run
   ```

   Flyway applies the schema and seeds 20 movies on first start.

## Tests

```bash
  mvn test
```

- Unit and `@WebMvcTest` tests need nothing else.
- Repository tests for movies and watch history use H2 in PostgreSQL mode (`test` profile).
- `ProfileRepositoryTest` (partial unique index) and `StreamingApiIntegrationTest` (end-to-end
  flows) start a throwaway PostgreSQL via Testcontainers, so Docker must be running.

## Swagger

`http://localhost:8080/swagger-ui.html`. Call `POST /api/v1/auth/register` or `/login`, then use
the returned token in the **Authorize** button.
