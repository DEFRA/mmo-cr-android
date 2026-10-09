# Reference-data backend: choosing a flavor and running it locally

This document is the practical companion to
[`docs/adr/0015-reference-data-api-integration.md`](../adr/0015-reference-data-api-integration.md) (read
that first for the *why* — networking stack, caching strategy, auth stub, and the open questions table).
This page is the *how*: which flavor to build, how to stand up a backend for it, and how to troubleshoot.

## Choosing a flavor

The `environment` product flavor dimension (`local` / `dev` / `prod`) selects which reference-data service
the app talks to, via the `REFERENCE_DATA_SERVICE_URL` `BuildConfig` field (`app/build.gradle.kts`):

| Flavor | Backend | `REFERENCE_DATA_SERVICE_URL` | When to use |
| ------ | ------- | ----------------------------- | ----------- |
| `local` | `DEFRA/mmo-cr-backend-local` Docker stack, on your machine | `http://10.0.2.2:3002/` (emulator) | Day-to-day feature work — fastest loop, works offline from DEFRA VPN |
| `dev`   | DEV environment (`mmo-cr-reference-data-service` on the DEFRA CDP platform) | `https://mmo-cr-catch-recording-service.api.dev.cdp-int.defra.cloud/reference-data-service/` | Verifying against the real shared environment; requires the DEFRA OpenVPN |
| `prod`  | — | `""` (deliberately empty) | Never for development — the network module fails fast with a clear exception rather than silently pointing production at DEV |

Build/test/install tasks are flavor-qualified, e.g. `./gradlew assembleDevDebug`,
`./gradlew testLocalDebugUnitTest`, `./gradlew installDevDebug` — see the main
[`README.md`](../../README.md) Quick start for the full command set.

## Running and seeding `DEFRA/mmo-cr-backend-local` (for the `local` flavor)

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
2. Add a gitignored override to the repo-root `local.properties` (never commit this file):
   ```properties
   referenceData.serviceUrl=http://192.168.1.23:3002/
   ```
   This is read by `app/build.gradle.kts` and overrides the `local`/`dev` flavor's default
   `REFERENCE_DATA_SERVICE_URL` at build time (never `prod` — its empty URL is not overridable). The
   trailing slash is mandatory (Retrofit requires it for a relative base URL).
3. For the `dev` flavor on a physical device, the device must be connected to the **DEFRA OpenVPN** (the
   DEV gateway is VPN-only) — the same override key can point at a different DEV-reachable host if needed,
   though the default `dev` URL normally needs no override.

## Checking DEV with curl

DEV's gateway is only reachable over the DEFRA OpenVPN. Once connected:

```bash
curl -s https://mmo-cr-catch-recording-service.api.dev.cdp-int.defra.cloud/reference-data-service/health
curl -s https://mmo-cr-catch-recording-service.api.dev.cdp-int.defra.cloud/reference-data-service/health/ready
```

`/health` and `/health/ready` sit at the **service root**, not under the `api/v1/reference-data/` API
path prefix — this is why the app carries a separate `REFERENCE_DATA_SERVICE_URL` (service root) alongside
the derived API base, rather than one combined base URL.

DEV's authentication is currently an **always-allow stub**: any non-empty `Authorization: Bearer <token>`
header is accepted (confirmed with `Authorization: Bearer local-stub` → `200`); a request with no header
gets `401`. This is not real authentication — see ADR 0015's "Token abstraction" section and its Q2 open
question.

## Troubleshooting

| Symptom | Likely cause | Fix |
| ------- | ------------ | --- |
| `401 unauthorized` | Missing/empty `Authorization` header | Confirm the flavor's `TokenProvider` binding — `local`/`dev` should use `StubTokenProvider`, not `MissingTokenProvider` (that's `prod`-only) |
| `503 reference_data_unavailable` (or `reference_store_unavailable`) | The backend isn't seeded yet | Run the bootstrap step above, then check `curl .../health/ready` reports `"ready": true` |
| A cleartext/`CLEARTEXT communication not permitted` error | Wrong flavor selected, or pointed at the wrong host | Cleartext is only permitted for `10.0.2.2`/`localhost` in debug builds (ADR 0015) — a real hostname (e.g. DEV) always needs HTTPS; rebuild with the correct flavor |
| `dataset_not_found` from the local backend | Floci hasn't been seeded (fresh volume, or after `down -v`) | Re-run the bootstrap step above |
| Backend containers can't reach each other | A service's `.env` points at `localhost`/`host.docker.internal` instead of the Docker service name | See `mmo-cr-backend-local`'s own troubleshooting section — cross-service calls must use Compose service names (e.g. `http://mmo-cr-authentication-service:3001`) |
