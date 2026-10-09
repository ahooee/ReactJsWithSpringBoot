# Base44 Dev Environment

## Project Overview

Spring Boot 4.0.6 backend (Java 21) — REST API with JPA/PostgreSQL, Spring Security
(JWT, stateless), springdoc OpenAPI/Swagger UI. No separate frontend; the `frontend/`
directory is empty. The app serves APIs and Swagger UI directly.

## Stack

- **Language:** Java 21 (Eclipse Temurin JDK 21)
- **Framework:** Spring Boot 4.0.6 (Gradle 9.5.1 wrapper)
- **Database:** PostgreSQL (local compose service, db `seconddb`, user `second`)
- **Auth:** JWT with in-memory signing key (`Keys.secretKeyFor`) — no secret env var needed
- **Live reload:** `./gradlew bootRun` (Spring devtools; restart container for code changes —
  Gradle continuous mode (`-t`) does not detect bind-mount file changes in Docker)

## Running

```bash
docker compose -f docker-compose.base44.yml up -d --build
```

The app listens on port **8080** inside the container, mapped to host **3000** for the
preview. First boot downloads Gradle + all Spring Boot dependencies (~5 min); subsequent
restarts are fast (deps cached in the `gradle-cache` volume).

## Verifying

- Health endpoint: `GET /api/hello` → `hello from TestController`
- Swagger UI: `/swagger-ui.html`
- API docs: `/api-docs`
- Root `/` returns a whitelabel 404 (no controller maps it) — this is expected.

## Database

Hibernate `ddl-auto=update` creates tables automatically on startup. The
`CommandLineRunner` in `SecondApplication` seeds roles and users on every start
(duplicates accumulate on restart — pre-existing app behavior, not a setup issue).

## No External Secrets

The app requires no external credentials. PostgreSQL credentials are inline in
`docker-compose.base44.yml`. JWT keys are generated in-memory at startup.

## Known Issues

- **Duplicate seed data on restart:** `SecondApplication`'s `CommandLineRunner` inserts
  users and roles on every startup without checking for existing records. Restarting the
  app creates duplicates, which breaks `findByUsername` (`NonUniqueResultException`) and
  login. To recover: `docker compose down`, `docker volume rm app_pgdata`, then `up -d`.
  This is a pre-existing app bug, not a setup issue.
- **Preview landing page:** A static `index.html` was added at `src/main/resources/static/`
  and `GET /`, `/index.html`, `/favicon.ico`, `/swagger-ui/**`, `/api-docs/**` were added to
  the `SecurityConfig` permitAll list so the app is viewable in the preview iframe.
