# Base44 Dev Environment

## Architecture

Three compose services (`docker-compose.base44.yml`):

| Service | What it is | Port |
| --- | --- | --- |
| `web` | React 18 + Vite 6 SPA (`frontend/`) | 5173 → **host 3000** (preview entry point) |
| `app` | Spring Boot 4 / Java 21 REST API (`src/`) | 8080 (internal only) |
| `db` | PostgreSQL 17 (`seconddb`, user `second`) | internal |

The browser only ever talks to `web`. Vite proxies `/api/**` and `/uploads/**` to
`http://app:8080`, so the SPA is single-origin and no CORS is involved at runtime.

## Running

```bash
docker compose -f docker-compose.base44.yml up -d --build
```

First boot downloads Gradle + Spring dependencies and runs `npm install` (~5 min);
both are cached in named volumes (`gradle-cache`, `web_node_modules`).

## Frontend (`frontend/`)

- Vite + React Router. Public routes: `/`, `/posts`, `/posts/:slug`, `/products`,
  `/products/:slug`, `/p/:slug` (CMS pages), `/login`, `/signup`.
- Admin routes: `/admin` (dashboard), `/admin/posts`, `/admin/pages`,
  `/admin/products`, `/admin/media`, `/admin/menus` — all behind `ProtectedRoute adminOnly`.
- Dark/light mode + accent themes are CSS-variable driven (`ThemeContext`,
  `data-theme` / `data-accent` on `<html>`, persisted in `localStorage`).
- Scroll animations use an `IntersectionObserver` (`components/Reveal.jsx`).
- Live reload: Vite HMR. Editing `vite.config.js` makes Vite restart itself.

### Proxy note (important)

`vite.config.js` strips the `Origin` header on proxied requests. Browsers attach
`Origin` to same-origin POSTs; forwarding it made Spring treat the proxied call as
cross-origin and reject it with **403**. Removing it keeps the API's CORS policy
strict while the proxy stays a same-origin gateway.

## Backend (`src/main/java/ir/linuxian/second`)

- Entities: `Post`, `Product`, `Media`, `Page`, `Menu`, `MenuItem`, `User`, `Role`.
- Auth: `POST /api/auth/login`, `POST /api/auth/signup`, `GET /api/auth/me`
  (Bearer JWT). `config/JwtAuthFilter` validates the token and grants
  `ROLE_<user.role>`; admin writes are gated with `hasRole("admin")`.
- Public reads: `GET /api/posts`, `/api/products`, `/api/pages`, `/api/menus`,
  `/api/media`, `/uploads/**`. `GET /api/posts/all` (includes drafts) is admin-only.
- Media: `POST /api/media/upload` (multipart) writes to `uploads/` at the repo root
  (gitignored, bind-mounted) and serves it at `/uploads/**` via `config/WebConfig`.
- Seeded admin account: **mohammad / linuxian**. Seeding is now idempotent (only
  runs on an empty database), which fixes the duplicate-user login bug.

## Verifying

```bash
curl localhost:3000/api/hello                     # -> hello from TestController
curl -X POST localhost:3000/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"mohammad","password":"linuxian"}'   # -> { token, role: "admin" }
curl localhost:3000/api/posts
```

Swagger UI (`/swagger-ui.html`) and `/api-docs` are only reachable on the API
service directly (`docker compose exec app curl localhost:8080/...`), not through
the Vite proxy.

## Notes / gotchas

- **Restart the API after Java changes:** `docker compose restart app`. Gradle
  continuous mode (`-t`) does not detect bind-mount file changes in Docker.
- JWTs are signed with an in-memory key, so every backend restart invalidates
  existing tokens (users must log in again). Pre-existing behavior.
- `uploads/` is runtime data — never commit it, never delete it to "reset" deps.
- Product "Add to cart" is a UI placeholder; there is no commerce backend yet.
- Hibernate `ddl-auto=update` creates new tables on startup automatically.

## No external secrets

The app needs no third-party credentials. PostgreSQL credentials are inline in the
compose file and the JWT key is generated in memory at startup.
