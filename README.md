# Liso

Liso is a video-first marketplace for Uganda. Buyers video-call a seller to see the item before paying, and every deal leaves a tamper-evident record. Mobile money (MTN MoMo, then Airtel Money) is held by Liso until the buyer confirms receipt.

The pilot is about 30 traders in Kampala, on entry-level Android phones (Tecno Spark, itel).

## What's here

| Folder | What it is |
| --- | --- |
| `backend/` | The API: TypeScript on Node 22, Fastify, Postgres. One service, split into modules. |
| `android/` | The Android app: Kotlin and Jetpack Compose, kept under 20 MB per phone. |
| `docs/` | Architecture, the milestone plan, and the [test server guide](docs/test-server.md). |
| `deploy/` | The test server's first-boot script. |

## Run the backend

```sh
docker compose up -d            # Postgres on localhost:5432
cd backend
cp .env.example .env
npm install
npm run dev                     # migrates, then serves on :8080
```

Sign-in codes are printed to the log while `SMS_PROVIDER=console`.

Tests need a database called `liso_test` (or set `TEST_DATABASE_URL`). They wipe it on every run.

```sh
npm test
```

## Build the Android app

Open `android/` in Android Studio, or run `./gradlew assembleDebug`. The debug build talks to `http://10.0.2.2:8080`, which is your computer as seen from the emulator.

CI builds the release APKs on every pull request and fails if any is over 20 MB.
