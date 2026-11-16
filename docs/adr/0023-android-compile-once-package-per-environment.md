# 0023 - Android: compile once, package per environment

## Status

Accepted (2026-11-04). Amends [0024](0024-packaged-app-configuration.md); implements the artifact side of
[0022](0022-android-three-app-release-topology.md).

## Context

Three Play apps (ADR 0022) have different package names, and each environment needs its own backend
configuration. Compiling per environment would produce six different binaries from one tag, and "what was
tested" would rest on build reproducibility alone. iOS compiles once and injects the stage configuration;
Android should do the same.

## Decision

One `compile` job builds a single **unsigned, config-free** `bundleRelease` (applicationId
`mmo.catchrecording.android`, versionCode N) and the `release-tools` CLI
([`build-logic/release-tools`](../../build-logic/release-tools)), and stores both as the `release-base`
artifact (35 days). Each environment job repackages that same base:

1. **`release-tools package`** rewrites the manifest `package`, every manifest attribute string that equals
   the old package or starts with the old package plus `.` (including `;`-separated provider authority
   lists; values such as `mmo.catchrecording.androidx…` are untouched), the versionCode, and the package
   name in the resource table `base/resources.pb`, and adds `base/assets/mmocr_app_config.json` with the
   environment's validated configuration. Nothing else changes, including the app label.
2. The bundle is signed with `jarsigner` using the upload key.
3. The signer certificate SHA-256 must equal the Environment variable `UPLOAD_CERT_SHA256`.
4. **`release-tools verify`** checks that every entry is byte-identical to the base except
   `base/manifest/AndroidManifest.xml`, `base/resources.pb`, `base/assets/mmocr_app_config.json` and
   `META-INF/*`; that the manifest and resource table equal the base after the expected changes; that the
   config equals exactly what was validated; and that bundletool validates the bundle.

The environment's configuration comes from `CR_APP_CFG_*` Environment variables, validated against
[`config/app-config.schema.json`](../../config/app-config.schema.json) by `CrRelease::AppConfig` before any
packaging. The app config Gradle plugin no longer adds the asset to release variants (debug is unchanged).

### Base provenance (the iOS archive-digest equivalent)

The base is never trusted just because it is present. Each step is chained to the one before it:

1. `compile` writes `base-provenance.json` (commit, tag, SHA-256 of `base.aab`) into the artifact and
   publishes the SHA-256 as the job output `base_sha256`.
2. Every download uses `digest-mismatch: error`, so the artifact must match the digest GitHub recorded at
   upload.
3. Every environment job refuses to continue, even before deciding to skip a stage already on Play,
   unless `sha256(base.aab)` equals both the record and `needs.compile.outputs.base_sha256`, and the
   record's commit and tag equal this run's `GITHUB_SHA` and tag. In CI the compile hash is mandatory.
4. On *Re-run all jobs*, `compile` restores the artifact instead of rebuilding and checks it against its
   record in the same way before publishing the hash again.

GitHub re-runs keep the original `GITHUB_SHA` and the outputs of jobs that are not re-run, so a resumed
run is verified against the hash from the original compile. A stage skipped because its build is already
on Play is not re-hashed: that build is signed, and its versionCode is unique to this tag and app.

### Rules for app code

App code must not use `BuildConfig.APPLICATION_ID` or `BuildConfig.VERSION_CODE`: both are rewritten after
compilation, so they would report the compiled base. Read them from `Context.packageName` and
`PackageManager`. A test guards this. The resource table of the compiled base names its package after the
applicationId (confirmed by `release-tools inspect`), which is why it is rewritten with the manifest.

## Deviations and fallback

- **The base artifact is not encrypted.** It contains no configuration and no secrets and the source is
  public, so confidentiality is not needed. iOS encrypts its base; this is a recorded deviation.
- **Fallback.** If Play rejects a renamed package for any app, compile once per app instead (a per-app
  Gradle property for the applicationId) and keep the packaging, signing and verification steps.

## Consequences

- The binary that reaches every environment shares identical dex, resources and native code; only the
  manifest identity, versionCode and config differ, and `verify` proves it.
- Adding a configuration value needs a schema entry and an Environment variable, not a rebuild per backend.
- Release tooling is JVM code with its own tests (`:build-logic:release-tools:test`), run by the `test` lane.
