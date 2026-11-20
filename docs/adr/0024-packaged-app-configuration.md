# 0024 - Packaged app configuration (replaces build flavors)

## Status

Accepted (release POC, 2026-10-14; renumbered from 0017, then 0021, after number clashes with ADRs on `main`) — amends
[0016](0016-reference-data-api-integration.md)'s "Product
flavors" section. Amended 2026-11-04 by [0023](0023-android-compile-once-package-per-environment.md):
release builds carry no asset; it is injected at packaging, per environment.

## Context

ADR 0016 chose the backend with `local`/`dev`/`prod` product flavors, each compiling a different
`REFERENCE_DATA_SERVICE_URL` / `USE_STUB_AUTH` into `BuildConfig`. The release design changes that:

- The POC assumed **one** Play app with a closed track per CDP backend; ADR 0022 replaced that with three
  Play apps and six environments.
- The target is "compile once, configure per track", the same model as the iOS app (iOS ADR-0020/0021).
  A value compiled into `BuildConfig` forces a recompile per backend.
- The iOS app reads one stage configuration (`MMOCRAppConfig`) whose values are GitHub Environment
  variables `CR_APP_CFG_<KEY>`, checked against a committed allow-list schema. Android should match it.

## Decision

1. **Flavors removed.** One variant per build type. `applicationId` is `mmo.catchrecording.android`;
   debug adds `.debug` so a developer build installs beside the Play build.
2. **Allow-list.** [`config/app-config.schema.json`](../../config/app-config.schema.json) is the only
   configuration file anyone maintains. Keys: `API_BASE_URL` (url, the API gateway base shared by all services; reference data is `<base>reference-data-service/`),
   `USE_STUB_AUTH` (bool), and the Entra sign-in keys (ADR 0019): `USE_FAKE_SIGN_IN` (bool),
   `MSAL_CLIENT_ID` (string), `MSAL_AUTHORITY_URL` (url), optional `MSAL_REDIRECT_URI` (string) and
   `MSAL_API_SCOPE` (string, the Entra API scope; blank fails sign-in fast). Entra
   values are public-client identifiers, not secrets. Same field rules as iOS: `type` (url/string/bool), `required`, `log`,
   `tracking` (must not be true), `description`; names containing SECRET/PASSWORD/TOKEN/PRIVATE/CREDENTIAL
   are rejected.
3. **Packaged asset.** Every build carries `assets/mmocr_app_config.json` with exactly the schema's keys,
   added by the `uk.gov.defra.mmocatchrecord.appconfig` Gradle plugin (`build-logic/app-config-plugin`):
   - **debug:** written from git-ignored `local.properties` keys `crAppCfg.<KEY>`; an unset key gives a
     build warning and an empty value (CI debug builds have none).
   - **release:** the compiled bundle has **no** asset. Fastlane validates the job's GitHub Environment
     `CR_APP_CFG_*` variables (`CrRelease::AppConfig`, a port of the iOS implementation) and
     `release-tools package` injects the asset into a copy of the base bundle, which `release-tools verify`
     then checks (ADR 0023).
4. **App behaviour.** The app reads the asset once. A missing or invalid value never falls back to another
   backend: reference-data networking fails fast on first use (the pattern ADR 0016 used for `prod`).
5. **Never a secret.** Only Environment *variables* are mapped; anything in the APK can be extracted.
   Values are Environment-level only, never repository or organisation level.

## Consequences

- One code path for every backend; adding a value needs a schema entry and an Environment variable, not a
  Gradle or workflow change.
- Compile-once is in place: the asset is written after compilation, per environment (ADR 0023).
- The workflow passes `toJSON(vars)` to one step and Fastlane keeps only allow-listed keys (as iOS D8).
- `BuildConfig.VERSION_CODE` and `BuildConfig.APPLICATION_ID` must not be used by the app: repackaging
  rewrites both after compilation, so the app reads them from `PackageManager` / `Context.packageName`.
