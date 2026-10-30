# Serlok server

Bun server untuk Identity, Room, Safety Session, location relay, dan heuristic shake incident.

## Setup

```powershell
copy .env.example .env.development
bun install
bun run db:migrate
bun run dev
```

Live `.env` files harus tetap lokal. Production configuration disimpan pada Heroku Config Vars.

## Verification

```powershell
bun run typecheck
bun run test:evidence
```

`test:evidence` membuat PostgreSQL test instance terisolasi, menerapkan migration, menjalankan test, lalu menulis ringkasan terpetakan (unit, REST API, WebSocket) dan menghentikan instance.

## Exposed routes

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/users/me`
- `GET /api/health`
- `GET /api/ready`
- `GET /ws`

Route landing, changelog, app link, REST Room listing, name update, audio, dan standalone History tidak disediakan.
