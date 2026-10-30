# Serlok

Snapshot source untuk aplikasi Android native, server Bun, dan script evidence kuantitatif. Dokumen skripsi, mockup, dan cache build tidak disertakan.

## Batas penelitian

Metode yang diimplementasikan adalah heuristic shake detection.

- Magnitudo accelerometer dinormalisasi menjadi G-Force.
- Threshold Room tersedia pada 2.5 g, 3.5 g, dan 4.5 g.
- Trigger memakai satu rising-edge crossing.
- Detector kembali armed di bawah 80 persen threshold aktif.
- Cooldown Android dan server adalah tiga detik.
- Lokasi, accelerometer, wake lock, dan SOS hanya aktif selama Safety Session.

Implementasi tidak mengklaim medical fall detection, population reliability, zero latency, atau jaminan keselamatan.

## Lingkup produk

Termasuk: authentication, temporary Room, Host-controlled Safety Session, live location, shake SOS, map, Room SOS history, Settings, dan G-Force Debug Overlay.

Tidak termasuk: manual SOS, Evidence Audio, Contact, standalone Incident History, owner resolution, battery relay, Host handoff, dan account deletion.

## Struktur

| Path             | Isi                                          |
| ---------------- | -------------------------------------------- |
| `android/serlok` | Aplikasi Kotlin dan Jetpack Compose          |
| `server`         | Bun, Hono, WebSocket, dan Drizzle PostgreSQL |
| `scripts`        | `evidence:all` dan renderer hasil kuantitatif |

## Prasyarat

- Bun 1.3.3
- PostgreSQL 18
- Android SDK

## Konfigurasi

Salin `server/.env.example` ke `server/.env.development`, `server/.env.test`, atau `server/.env.production`. File live `.env` wajib tetap lokal. Secret production disimpan pada Heroku Config Vars.

Key yang didokumentasikan:

- `BUN_ENV`
- `DATABASE_URL`
- `JWT_SECRET` (minimal 32 karakter)
- `PORT`

Untuk Android, salin `android/serlok/local.properties.example` ke `android/serlok/local.properties` dan isi `sdk.dir`. File itu tidak boleh masuk Git.

Endpoint default Android adalah `https://serlok.sonangga.dev` dan `wss://serlok.sonangga.dev`. Override lokal memakai `SERLOK_HTTP_URL` dan `SERLOK_WS_URL`.

## Setup

```powershell
cd server
copy .env.example .env.development
bun install
bun run db:migrate
bun run dev
```

```powershell
cd android\serlok
copy local.properties.example local.properties
.\gradlew.bat assembleDebug
```

## Verifikasi

```powershell
cd server
bun run typecheck
bun run test:evidence

cd ..\android\serlok
.\gradlew.bat testDebugUnitTest assembleDebug

cd ..\..
bun run evidence:all
```

`test:evidence` membuat PostgreSQL test instance terisolasi, menerapkan migration, menjalankan test, lalu menghentikan instance. `evidence:all` menulis artefak ke `evidence/`.
