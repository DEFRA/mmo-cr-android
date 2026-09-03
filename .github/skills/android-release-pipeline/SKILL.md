---
name: android-release-pipeline
description: "Build or extend the MMO Catch Recording Android CI/CD pipeline: GitHub Actions PR-validation and tag-triggered release workflows, Fastlane lanes (build/version/Play tracks), SonarCloud, GitHub Environments with manual approval gates, Play App Signing + upload key, versioning, and the separate CodeQL advanced-setup workflow + Dependabot config. Use when creating or changing any pipeline, workflow, Fastlane, signing, versioning or release-management artefact. Android-only (never iOS)."
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
   `gradle/libs.versions.toml`, product flavors and `applicationId`, and existing ADRs under `docs/adr/`.
2. **Confirm the frozen model** and record/keep the ADRs (create any that are missing **before**
   finalising the pipeline):
   - **Configuration strategy (frozen): build-time Option B** — three separate apps, each compiled as its
     own product flavor; promote the **commit**, not a single binary.
   - **applicationId / versioning (frozen):** three identities — `mmo.catchrecording.android.dev` /
     `mmo.catchrecording.android.test` / `mmo.catchrecording.android` — as three Google Play apps;
     `versionName` from the tag, `versionCode` from the release run number, per-app namespaces.
   - **Signing strategy:** Play App Signing (Google holds the app signing key) + a per-app **upload key**
     (base64 keystore decoded into a temporary keystore in CI).
3. **Plan and get approval** before creating files (Standard/Complex work).

## Target repository layout
Single-app Android repo (app sources under `app/src/main`, tests under `app/src/test` and
`app/src/androidTest`). Place pipeline artefacts as:

```
.github/
  workflows/
    android-ci.yml      # PR + main: ktlint/detekt/lint, build, test+coverage (Kover), SonarCloud
    android-release.yml # tag / dispatch: one run, six gated per-environment jobs (Fastlane)
    codeql.yml          # SEPARATE CodeQL advanced-setup SAST workflow (java-kotlin)
  dependabot.yml        # SEPARATE Dependabot config (github-actions, gradle, bundler)
fastlane/
  Appfile
  Fastfile
Gemfile                 # pins fastlane (+ plugins) via Bundler
Gemfile.lock
sonar-project.properties
docs/adr/               # signing + release-model ADRs
```

## Procedure

### 1. Pin the toolchain (Bundler + Gradle)
Create a `Gemfile` pinning `fastlane` (and any plugins). Commit `Gemfile.lock`. CI installs with
`bundle install` and runs lanes via `bundle exec fastlane …`. Pin the JDK (Temurin 17), the Android SDK and
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
- Triggers: `push` tags matching `android-v*`, plus `workflow_dispatch` (with a marketing-version input).
- **One workflow run, six sequential gated jobs.** A GitHub Environment approval gates the **start of a
  job**, so each distinct manual approval is its own job/environment. Every build job builds from the
  **same tagged commit**; the promotion jobs never rebuild.
  - `dev-build-internal` → `environment: dev` (no gate) → build **Dev** AAB → Dev **internal** track.
  - `test-build-internal` → `environment: test` (Approval A) → build **Test** AAB → Test **internal** track.
  - `test-promote-closed` → `environment: test-closed` (Approval B) → promote the **same** Test build → **closed** UAT track (no rebuild).
  - `prod-build-internal` → `environment: prod` (Approval C) → build **Prod** AAB → Prod **internal** track.
  - `prod-promote-closed` → `environment: prod-closed` (Approval D) → promote the **same** Prod build → **closed** track (no rebuild).
  - `prod-promote-production` → `environment: prod-production` (Approval E) → promote the **same** Prod build → **production** track (staged rollout).
- Derive `versionName` from the tag and `versionCode` from the **release** `GITHUB_RUN_NUMBER` (do **not**
  query the Play API). Embed the short Git SHA + tag as a read-only `BuildConfig` field (e.g.
  `GIT_COMMIT_SHA`) for traceability — not as the version code.
- Never cancel an in-flight release (`concurrency` with `cancel-in-progress: false`).
- **Build-per-environment from one tested commit.** The three apps have distinct `applicationId`s, so a
  single AAB cannot move between environments; equivalence is evidenced by the same commit SHA, pinned
  toolchain and locked dependencies. Track promotion within a Play app is a no-rebuild Google Play metadata
  action (`track_promote_to`).

### 5. Fastlane
- **`Appfile`** — `json_key_file`/`package_name` per Play app (no secrets committed).
- **`Fastfile`** lanes:
  - `test` — run unit/UI tests (`gradle` action) (used by CI).
  - A parametrised private `build_and_upload` — decode the **upload keystore** into a temporary keystore,
    `gradle(task: "bundle", flavor + Release, xcargs equivalent via `-P` version props)` to build the AAB
    for the environment's flavor, then `upload_to_play_store(track: "internal", aab: <path>,
    json_key_data: ...)` to that app's **internal** track.
  - Per-environment release lanes (`release_dev` / `release_test` / `release_prod`) call `build_and_upload`
    with the matching flavor/`applicationId`/service account.
  - `promote_closed` — **no rebuild**: `upload_to_play_store(track: "internal", track_promote_to: "closed",
    version_code: <code>)` for that app.
  - `promote_production` — promote the **Prod** app's build to `production` with a **staged rollout**
    (`rollout: "0.1"`). Uploading to a track and promoting to production are separate actions.
- **Signing:** Play App Signing holds the app signing key; CI holds only the **upload key**. Decode a
  base64 keystore into a temporary keystore, reference it via a Gradle signing config, and delete it at job
  end. Never commit signing assets; authenticate to Play with a **service-account JSON key**
  (`json_key_data`), never an interactive login.

### 6. GitHub Environments & secrets
- Create **six** Environments — **`dev`** (ungated), **`test`** (A), **`test-closed`** (B), **`prod`**
  (C), **`prod-closed`** (D) and **`prod-production`** (E) — each gated (except `dev`) by **required
  reviewer(s)** (prevent self-approval where supported) and restrict deployments to `main` + `android-v*`
  tags.
- Scope release secrets to each Environment (not the repo), exposing only what that stage needs:
  `PLAY_SERVICE_ACCOUNT_JSON`, `UPLOAD_KEYSTORE_BASE64`, `UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_ALIAS`,
  `UPLOAD_KEY_PASSWORD`; plus `SONAR_TOKEN` (keep SonarCloud credentials separate from signing/release
  credentials).
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
Record the release ADRs under `docs/adr/`: **build-time configuration & environment promotion** (Option B),
the **three-application applicationId & environment model** (`dev`/`test`/`prod` as separate Play apps), the
**signing strategy** (Play App Signing + upload keys), the **build-per-environment promotion &
commit-equivalence** policy, the **single-workflow six-environment release topology** (per-stage +
per-track-promotion gates), and the **release model** (trunk-based, tag-driven, no release branches). Add
ADRs for cloud real-device testing (UK data residency) only if adopted. Update the README with the release
process, required secrets, environments and how to cut a release.

## Standards to honour
- [ci-cd instructions](../../instructions/ci-cd.instructions.md) (tooling, branching, versioning,
  environments, signing, secrets, native-vs-workflow security controls)
- [security instructions](../../instructions/security.instructions.md) (Keystore/keystore hygiene, TLS,
  never commit secrets, credential-exposure process)
- The dev→prod release-management flow in [references/release-management.md](references/release-management.md)

## Validate
- `actionlint` (if available) passes; workflows are least-privilege and pin Action/tool versions.
- `bundle exec fastlane lanes` lists the lanes; the `test` lane runs green.
- A dry build succeeds (`./gradlew bundleProdRelease`) and a `supply --validate_only`/draft upload works
  **without** a real production rollout.
- Secrets resolve from the correct Environment and never appear in logs.
- No release branch introduced; no iOS tooling added.
