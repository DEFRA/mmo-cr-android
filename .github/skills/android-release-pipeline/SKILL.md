---
name: android-release-pipeline
description: "Build or extend the MMO Catch Recording Android CI/CD pipeline: GitHub Actions PR-validation and manually dispatched release workflows, Fastlane lanes (compile once, package/sign/verify per environment, Play upload and promotion), SonarCloud, GitHub Environments with reviewer gates, Play App Signing + upload keys, versioning, and the separate CodeQL advanced-setup workflow + Dependabot config. Use when creating or changing any pipeline, workflow, Fastlane, signing, versioning or release-management artefact. Android-only (never iOS)."
argument-hint: "e.g. 'scaffold the PR CI + release pipeline' or 'add closed-track UAT testing'"
user-invocable: false
---

# Android release pipeline

Use this skill to stand up or extend the app's delivery pipeline. It is **Android-only** — never add iOS
tooling. Follow [ci-cd.instructions.md](../../instructions/ci-cd.instructions.md) (the rulebook) and
[security.instructions.md](../../instructions/security.instructions.md) throughout, and obey the §4
working framework: plan → get approval → implement → validate.

## When to use
- First-time pipeline scaffolding for the repo.
- Adding/altering a CI or release stage, a Fastlane lane, a GitHub Environment, or the signing/versioning
  setup.
- Adding the CodeQL advanced-setup workflow or the Dependabot config.

## Before you build (Confirm tech stack → Read → Clarify → Plan)
0. **Tech-stack confirmation gate (mandatory, copilot-instructions §5.1).** The pipeline/identity/signing
   decisions are **tentative** until confirmed. Before creating any pipeline/config/signing file, check the
   **Android DevOps** agent's `Tech-stack confirmation status` block: if it reads `❌ Not yet confirmed`,
   **stop and prompt the developer / release owner** to confirm/amend the pipeline stack, record the
   decisions as ADRs, then flip that agent file's status to `✅ Confirmed …`. Do not build the pipeline until
   this gate is passed.
1. **Read** the repo: existing `.github/workflows/`, any `fastlane/`, `Gemfile`, `build.gradle.kts`,
   `gradle/libs.versions.toml`, `version.properties`, `build-logic/release-tools`, `fastlane/lanes` and
   `fastlane/lib/cr_release`, and existing ADRs under `docs/adr/`.
2. **Confirm the frozen model** and record/keep the ADRs (create any that are missing **before**
   finalising the pipeline):
   - **Configuration strategy (frozen): compile once, package per environment** (ADR 0023) — no flavors;
     one config-free base bundle repackaged per environment.
   - **Topology / identity / versioning (frozen, ADR 0022):** three Google Play apps
     (`mmo.catchrecording.dev.android` / `mmo.catchrecording.test.android` / `mmo.catchrecording.android`), six
     environments, Managed Publishing on the Prod app only; `VERSION_CODE` = build number N (≤ 9999) and
     uploaded code = `channel × 10 000 + N`.
   - **Signing strategy:** Play App Signing (Google holds the app signing key) + upload keys (non-prod key for
     Dev/Test apps, Prod key for the Prod app), decoded into a temporary keystore in CI. One Play service
     account for all apps is accepted deviation DA-1.
3. **Plan and get approval** before creating files (Standard/Complex work).

## Target repository layout
Single-app Android repo (app sources under `app/src/main`, tests under `app/src/test` and
`app/src/androidTest`). Place pipeline artefacts as:

```
.github/
  workflows/
    android-ci.yml      # PR + main: ktlint/detekt/lint, build, test+coverage (Kover), SonarCloud
    android-release.yml # workflow_dispatch on a v* tag: compile, then six gated environment jobs (Fastlane)
    codeql.yml          # SEPARATE CodeQL advanced-setup SAST workflow (java-kotlin)
  dependabot.yml        # SEPARATE Dependabot config (github-actions, gradle, bundler)
build-logic/release-tools/  # JVM CLI: inspect / package / verify (compile-once repackaging)
fastlane/
  Appfile
  Fastfile              # imports lanes/*.rb; logic in lib/cr_release/*.rb
  release-notes/production/en-GB.txt  # optional cy.txt, <= 500 chars each
Gemfile                 # pins fastlane (+ plugins) via Bundler
Gemfile.lock
version.properties      # VERSION_NAME + VERSION_CODE (build number N)
sonar-project.properties
docs/adr/               # signing + release-model ADRs (0022, 0023)
```

## Procedure

### 1. Pin the toolchain (Bundler + Gradle)
Create a `Gemfile` pinning `fastlane` (and any plugins). Commit `Gemfile.lock`. CI installs with
`bundle install` and runs lanes via `bundle exec fastlane …`. Pin the JDK (Temurin 25), the Android SDK and
build-tools, and use the Gradle wrapper.

### 2. PR CI workflow — `.github/workflows/android-ci.yml`
- Triggers: `pull_request` to `main` and `push` to `main`.
- `runs-on: ubuntu-latest`; set up JDK 17; cache Gradle.
- Steps: **ktlint/detekt/Android Lint** → **build** (`./gradlew assembleDebug`) → **unit tests with
  coverage** (`./gradlew testDebugUnitTest koverXmlReport`) → **instrumented/UI tests** on an emulator
  (`reactivecircus/android-emulator-runner`) or a Gradle Managed Device → **SonarCloud** scan (upload Kover
  coverage; enforce the quality gate).
- `permissions:` least-privilege; `concurrency:` cancels superseded PR runs.

### 3. SonarCloud
Add `sonar-project.properties` (DEFRA organisation + project key). Point Sonar at the Kover XML report and
run the scan in `android-ci.yml`. The SonarCloud quality gate is the coverage/quality source of truth; wire
it as a required check on `main`.

### 4. Release workflow — `.github/workflows/android-release.yml`
- Trigger: `workflow_dispatch` on a `v*` tag with boolean inputs `dev`, `test`, `prod` (default true) and
  `distribution` (`manual` | `play`, default `manual`). `permissions: contents: read`; `concurrency` with
  `cancel-in-progress: false`.
- Jobs: `compile` (no Environment) → `dev` → `test` → `perf-test` → `uat` → `prep-prod` → `production`.
  Each Environment job runs only if `compile` succeeded, its input is true and the previous job succeeded or
  was skipped; `perf-test` needs `test` = success, `prep-prod` needs `uat` = success, `production` needs
  `prep-prod` = success. There is **no go-live job** (Publish changes is a manual Play Console click).
  - `compile`: validate the tag, `bundle exec fastlane compile_release` (one unsigned, config-free
    `bundleRelease` + the `release-tools` CLI), upload the `release-base` artifact (35 days).
  - Environment jobs: download `release-base`, `bundle exec fastlane release_environment env:… distribution:…`
    (repackage → sign → check `UPLOAD_CERT_SHA256` → `release-tools verify` → upload, or keep the AAB as a
    7-day artifact in `manual` mode), then `cleanup-signing-assets.sh` with `if: always()`.
  - `production`: `bundle exec fastlane promote_production distribution:…` — promotes the versionCode already
    on `prep-prod`; never uploads.
- Version from `version.properties`: N = `VERSION_CODE` (≤ 9999), uploaded code = `channel × 10 000 + N`,
  tag `v<VERSION_NAME>-BUILD_<N>` created by `android-ci.yml` on `main` only. Do **not** query the Play API to
  choose a version. `GIT_COMMIT_SHA` is traceability only.
- Pass inputs and secrets through `env:`, never interpolate them into `run:`. Write tag, commit, package,
  versionCode and SHA-256s to `$GITHUB_STEP_SUMMARY`.

### 5. Fastlane
- **Lanes** (`fastlane/lanes/*.rb`, logic in `fastlane/lib/cr_release/`): `lint`, `build`, `test`,
  `instrumented_test` (CI); `compile_release`; `release_environment env:<dev|test|perf-test|uat|prep-prod>
  distribution:<manual|play>`; `promote_production distribution:<manual|play>`; private
  `package_environment` and `sign_bundle`.
- **Play access** is `CrRelease::PlayPublisher` over the Google Play Developer API (`supply` cannot set
  `changesInReviewBehavior`): `version_codes`, `upload`, `promote`. Non-internal commits use
  `ERROR_IF_IN_REVIEW`; the edit is always deleted on failure; re-runs are idempotent.
- **Signing:** Play App Signing holds the app signing key; CI holds only the **upload key** (non-prod key for
  dev/test/perf-test, Prod key for uat/prep-prod). Decode the base64 keystore into a 0600 temporary file,
  sign with `jarsigner` (passwords via `-storepass:env`), check the certificate against
  `UPLOAD_CERT_SHA256`, delete the keystore at job end. Never commit signing assets.

### 6. GitHub Environments & secrets
- Create **six** Environments — `dev` (ungated), `test`, `perf-test`, `uat`, `prep-prod`, `production` —
  each gated (except `dev`) by **required reviewers** with self-review prevented, deployments allowed only
  from `v*` tags.
- Packaging Environments (`dev`, `test`, `perf-test`, `uat`, `prep-prod`): secrets `UPLOAD_KEYSTORE_BASE64`,
  `UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_ALIAS`, `UPLOAD_KEY_PASSWORD`, `PLAY_SERVICE_ACCOUNT_JSON`;
  variables `CR_APP_CFG_API_BASE_URL`, `CR_APP_CFG_USE_STUB_AUTH`, `CR_APP_CFG_USE_FAKE_SIGN_IN`,
  `CR_APP_CFG_MSAL_*` (Entra, ADR 0019), `UPLOAD_CERT_SHA256`. `production` needs
  only `PLAY_SERVICE_ACCOUNT_JSON`. The Prod upload key must not be in `dev`. `SONAR_TOKEN` stays a
  repository secret, separate from release credentials.
- Never echo secrets; rely on masking; keep `set -x` away from secret-bearing steps.

### 7. CodeQL — separate advanced-setup workflow `.github/workflows/codeql.yml`
Maintain CodeQL as its **own** workflow (not folded into CI or release):
- Language `java-kotlin`; `runs-on: ubuntu-latest`; triggers on `pull_request`/`push` to `main` and a
  weekly `schedule`.
- Give it an **explicit Gradle build** (init → `./gradlew assembleDebug` → analyze) so analysis is
  reproducible; `permissions: security-events: write`.
- (If the explicit build proves unnecessary, GitHub's *default setup* in Settings is the simpler fallback —
  but this repo keeps the advanced-setup workflow for control.)

### 8. Dependabot — separate config `.github/dependabot.yml`
Maintain Dependabot as its **own** native config file (it is **not** a GitHub Actions workflow):
- Ecosystems: `github-actions` (workflow Action updates), `gradle` (app dependencies), `bundler` (Fastlane
  gems).
- Weekly schedule; sensible open-PR limits. Optionally add a small auto-merge workflow for patch/minor
  security updates.

### 9. Native security toggles (confirm, no file)
Confirm **secret scanning + push protection** are enabled in *Settings → Code security*. If a secret ever
leaks, follow the DEFRA
[credential exposure](https://defra.github.io/software-development-standards/processes/credential_exposure/)
process.

### 10. ADRs & README
Record the release ADRs under `docs/adr/`: the **three-app release topology** (ADR 0022: three Play apps,
six environments, Managed Publishing on the Prod app only, versioning, DA-1), **compile once, package per
environment** (ADR 0023), the **packaged app configuration** (ADR 0024) and the **signing strategy** (Play
App Signing + upload keys). Add ADRs for cloud real-device testing (UK data residency) only if adopted.
Update the README *Release* section with the process, required secrets/variables, environments and the
manual Play Console runbook.

## Standards to honour
- [ci-cd instructions](../../instructions/ci-cd.instructions.md) (tooling, branching, versioning,
  environments, signing, secrets, native-vs-workflow security controls)
- [security instructions](../../instructions/security.instructions.md) (Keystore/keystore hygiene, TLS,
  never commit secrets, credential-exposure process)
- The dev→prod release-management flow in [references/release-management.md](references/release-management.md)

## Validate
- `actionlint` (if available) passes; workflows are least-privilege and pin Action/tool versions.
- `bundle exec fastlane lanes` lists the lanes; the `test` lane runs green.
- A local dry run works without Play: `compile_release`, then `release_environment` with
  `distribution:manual`, then check the package, versionCode and config asset of the output AAB.
  Never perform a real Play upload or production promotion to test the pipeline.
- Secrets resolve from the correct Environment and never appear in logs.
- No release branch introduced; no iOS tooling added.
