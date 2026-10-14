# Reference-data backend: choosing a backend and running it locally

This document is the practical companion to
[`docs/adr/0016-reference-data-api-integration.md`](../adr/0016-reference-data-api-integration.md) (read
that first for the *why* — networking stack, caching strategy, auth stub, and the open questions table).
This page is the *how*: which backend to point a debug build at, how to stand up a backend for it, and how
to troubleshoot.

## Choosing a backend

There are no build flavors any more ([ADR 0017](../adr/0017-packaged-app-configuration.md)). The backend
comes from the packaged `mmocr_app_config.json` asset. For a **debug** build it is written from two
git-ignored keys in the repo-root `local.properties` (never commit this file):

```properties
crAppCfg.API_BASE_URL=https://mmo-cr-catch-recording-service.api.dev.cdp-int.defra.cloud/reference-data-service/
crAppCfg.USE_STUB_AUTH=true
```

| Backend | `crAppCfg.API_BASE_URL` | When to use |
| ------- | ----------------------- | ----------- |
| `DEFRA/mmo-cr-backend-local` Docker stack, on your machine | Not currently supported: its HTTP endpoint is blocked by the app's network security configuration | Use the HTTPS DEV endpoint until the local stack supports TLS |
| DEV environment (`mmo-cr-reference-data-service` on the DEFRA CDP platform) | `https://mmo-cr-catch-recording-service.api.dev.cdp-int.defra.cloud/reference-data-service/` | Verifying against the real shared environment; requires the DEFRA OpenVPN |

Both backends currently need `crAppCfg.USE_STUB_AUTH=true` (DEV's auth is a stub). An unset key gives a
build warning and the app fails fast on first reference-data call rather than guessing a backend. Release
builds never read `local.properties`: the release pipeline packages the GitHub Environment's values.

Tasks are plain debug tasks, e.g. `./gradlew assembleDebug`, `./gradlew testDebugUnitTest`,
`./gradlew installDebug` — see the main [`README.md`](../../README.md) Quick start.

## Running and seeding `DEFRA/mmo-cr-backend-local` (for the local backend)

Clone [`DEFRA/mmo-cr-backend-local`](https://github.com/DEFRA/mmo-cr-backend-local) alongside this repo and
follow its own README; the key steps are summarised here (check that repo's README for anything that has
since changed):

1. **Prerequisite:** Docker Desktop (or equivalent) with Docker Compose v2.24+.
2. **Configure `.env` files** — `mmo-cr-authentication-service/.env` can stay empty (local always-allow
   auth stub, safe defaults). Copy `mmo-cr-reference-data-service/.env.sample` to `.env` if it doesn't
   already exist; its defaults are correct for this app (it only needs to reach Floci and the
   authentication service as Docker containers on the shared network, not via `localhost`).
3. **Start everything:** `docker compose up -d` from the `mmo-cr-backend-local` repository root.
4. **Seed reference data (first run only)** — Floci (the local S3 emulator) starts empty:
   ```bash
   docker compose exec mmo-cr-reference-data-service npm run reference-data:bootstrap
   docker compose restart mmo-cr-reference-data-service
   ```
   This only needs repeating after `docker compose down -v` or `npm run floci:reset` — the seed data
   persists in the `floci-data` volume across normal `down`/`up` cycles and restarts.
5. **Verify:**
   ```bash
   curl -s http://localhost:3002/health | jq
   curl -s http://localhost:3002/health/ready | jq   # should report "ready": true, all datasets loaded
   ```

## Why `10.0.2.2` (and the physical-device alternative)

The Android emulator runs in its own virtual network; `10.0.2.2` is the emulator's fixed alias for **the
host machine's `localhost`**, so `http://10.0.2.2:3002/` reaches the `local` backend's host-published port
from inside the emulator. `localhost`/`127.0.0.1` *inside* the emulator would instead mean the emulator
itself, which is not where the backend is running.

A **physical device** cannot resolve `10.0.2.2` at all (it isn't virtualised) — instead:

1. Find your machine's LAN IP (e.g. `ipconfig` on Windows, look for the Wi-Fi/Ethernet adapter's IPv4
   address) and ensure the device is on the same network.
2. Point the git-ignored `local.properties` at it:
   ```properties
   crAppCfg.API_BASE_URL=http://192.168.1.23:3002/
   ```
   The trailing slash is mandatory (Retrofit requires it for a relative base URL). Plain `http://` works only
   in debug builds, and only for hosts the debug network security config allows (`10.0.2.2`, `localhost`) —
   add the LAN IP there locally if needed, never in a commit.
3. For the DEV backend on a physical device, the device must be connected to the **DEFRA OpenVPN** (the
   DEV gateway is VPN-only).

## Checking DEV with curl

DEV's gateway is only reachable over the DEFRA OpenVPN. Once connected:

```bash
curl -s https://mmo-cr-catch-recording-service.api.dev.cdp-int.defra.cloud/reference-data-service/health
curl -s https://mmo-cr-catch-recording-service.api.dev.cdp-int.defra.cloud/reference-data-service/health/ready
```

`/health` and `/health/ready` sit at the **service root**, not under the `api/v1/reference-data/` API
path prefix — this is why the app's `API_BASE_URL` config value is the service root, from which the API
base is derived, rather than one combined base URL.

DEV's authentication is currently an **always-allow stub**: any non-empty `Authorization: Bearer <token>`
header is accepted (confirmed with `Authorization: Bearer local-stub` → `200`); a request with no header
gets `401`. This is not real authentication — see ADR 0016's "Token abstraction" section and its Q2 open
question.

## Troubleshooting

| Symptom | Likely cause | Fix |
| ------- | ------------ | --- |
| `401 unauthorized` | Missing/empty `Authorization` header | Check `crAppCfg.USE_STUB_AUTH=true` in `local.properties` (or `CR_APP_CFG_USE_STUB_AUTH` for a release), so `StubTokenProvider` is bound |
| `503 reference_data_unavailable` (or `reference_store_unavailable`) | The backend isn't seeded yet | Run the bootstrap step above, then check `curl .../health/ready` reports `"ready": true` |
| A cleartext/`CLEARTEXT communication not permitted` error | `http://` to a host other than `10.0.2.2`/`localhost`, or a release build | Cleartext is only permitted for `10.0.2.2`/`localhost` in debug builds (ADR 0016) — a real hostname (e.g. DEV) always needs HTTPS |
| `dataset_not_found` from the local backend | Floci hasn't been seeded (fresh volume, or after `down -v`) | Re-run the bootstrap step above |
| Backend containers can't reach each other | A service's `.env` points at `localhost`/`host.docker.internal` instead of the Docker service name | See `mmo-cr-backend-local`'s own troubleshooting section — cross-service calls must use Compose service names (e.g. `http://mmo-cr-authentication-service:3001`) |
