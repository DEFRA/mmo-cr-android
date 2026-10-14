# 0017 - Packaged app configuration (replaces build flavors)

## Status

Accepted (release POC, 2026-10-14) — amends [0016](0016-reference-data-api-integration.md)'s "Product
flavors" section.

## Context

ADR 0016 chose the backend with `local`/`dev`/`prod` product flavors, each compiling a different
`REFERENCE_DATA_SERVICE_URL` / `USE_STUB_AUTH` into `BuildConfig`. The release design changes that:

- Android ships **one** Play app, `mmo.catchrecording.android`, with one closed-testing track per CDP backend
  (dev, test, perf-test, ext-test, prod-rc) — not one app per environment.
- The target is "compile once, configure per track", the same model as the iOS app (iOS ADR-0020/0021).
  A value compiled into `BuildConfig` forces a recompile per backend.
- The iOS app reads one stage configuration (`MMOCRAppConfig`) whose values are GitHub Environment
  variables `CR_APP_CFG_<KEY>`, checked against a committed allow-list schema. Android should match it.

## Decision

1. **Flavors removed.** One variant per build type. `applicationId` is `mmo.catchrecording.android`;
   debug adds `.debug` so a developer build installs beside the Play build.
2. **Allow-list.** [`config/app-config.schema.json`](../../config/app-config.schema.json) is the only
   configuration file anyone maintains. Keys: `API_BASE_URL` (url, the reference-data service root) and
   `USE_STUB_AUTH` (bool). Same field rules as iOS: `type` (url/string/bool), `required`, `log`,
   `tracking` (must not be true), `description`; names containing SECRET/PASSWORD/TOKEN/PRIVATE/CREDENTIAL
   are rejected.
3. **Packaged asset.** Every build carries `assets/mmocr_app_config.json` with exactly the schema's keys,
   added by the `uk.gov.defra.mmocatchrecord.appconfig` Gradle plugin (`build-logic/app-config-plugin`):
   - **debug:** written from git-ignored `local.properties` keys `crAppCfg.<KEY>`; an unset key gives a
     build warning and an empty value (CI debug builds have none).
   - **release:** Fastlane validates the job's GitHub Environment `CR_APP_CFG_*` variables
     (`CrRelease::AppConfig`, a port of the iOS implementation) **before** compiling, writes them to
     `build/release/app-config.json`, and passes `-PcrAppConfigFile`. A release build without it fails.
     After the build the lane checks the AAB carries exactly those values.
4. **App behaviour.** The app reads the asset once. A missing or invalid value never falls back to another
   backend: reference-data networking fails fast on first use (the pattern ADR 0016 used for `prod`).
5. **Never a secret.** Only Environment *variables* are mapped; anything in the APK can be extracted.
   Values are Environment-level only, never repository or organisation level.

## Consequences

- One code path for every backend; adding a value needs a schema entry and an Environment variable, not a
  Gradle or workflow change.
- The POC still compiles per release run; moving the asset write after compilation (inject into the AAB
  with a rewritten versionCode) is the follow-up for true compile-once across tracks.
- The workflow passes `toJSON(vars)` to one step and Fastlane keeps only allow-listed keys (as iOS D8).
- `BuildConfig.VERSION_CODE` must not be used by the app: the future repackaging step rewrites the
  versionCode after compilation, so the app reads it from `PackageManager`.
