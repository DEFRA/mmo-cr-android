---
description: "CI/CD and release-engineering standards for the MMO Catch Recording Android app: GitHub Actions + Fastlane on GitHub-hosted Ubuntu runners, trunk-based development with tag-driven releases, SemVer + versionCode, gated GitHub Environments, Play App Signing, secrets management, SonarCloud, and the GitHub-native security features (CodeQL, Dependabot, secret scanning). Use when creating or reviewing pipelines, workflows, Fastlane config, signing, versioning or release management."
applyTo: ".github/workflows/**, .github/dependabot.yml, fastlane/**, **/*.gradle, **/*.gradle.kts, **/gradle/libs.versions.toml, **/Gemfile, **/Gemfile.lock, **/Appfile, **/Fastfile"
---

# CI/CD & release-engineering standards (Android)

These standards govern continuous integration, delivery and release engineering for the **MMO Catch
Recording native Android app**. This is an **Android-only** repository — iOS is delivered from a separate
repository, so **never add iOS tooling, Xcode, Fastlane iOS lanes, TestFlight/App Store Connect or
cross-platform build matrices here**.

Precedence follows [copilot-instructions.md](../copilot-instructions.md): **DEFRA > GDS > Google/Android >
community**. The mandatory DEFRA constraints (offline-first, encryption in transit, protect data at rest,
error logging, code-in-the-open, never commit secrets, WCAG 2.2 AA, Secure by Design) all still apply to
release engineering. Any deviation from a DEFRA standard must be raised as a governance exception
(Delivery Architecture: `delivery.architecture@defra.gov.uk`).

## Tooling (fixed decisions)

- **CI orchestrator:** **GitHub Actions** is the single authoritative CI/CD orchestration and audit
  platform. All PR validation, main-branch validation and tag-triggered releases run here. GitHub Actions
  decides **when** a job runs and **with what permissions**; Fastlane does the Play-specific work.
- **Deployment engine:** **Fastlane** — chosen to keep one uniform automation model across the Android and
  (separately-hosted) iOS apps. It is **not** a second orchestrator. Build, repackage, sign, verify and
  upload/promote to Google Play are driven by Fastlane lanes (`compile_release`, `release_environment`,
  `promote_production`); the AAB is built by Gradle and repackaged by the `release-tools` CLI
  (`build-logic/release-tools`). Play uploads use `CrRelease::PlayPublisher` (the Google Play Developer API
  client) because `supply` cannot set `changesInReviewBehavior` (ADR 0022). Do **not** introduce a second
  deployment mechanism.
- **Build system:** **Gradle (Kotlin DSL)** with a version catalog (`gradle/libs.versions.toml`). Build an
  **Android App Bundle (AAB)** for release (`bundleRelease`), never a bare APK for Play upload.
- **Build infrastructure:** **GitHub-hosted Ubuntu runners**, pinned to a specific image
  (e.g. `ubuntu-24.04`) rather than the floating `ubuntu-latest`, so builds stay reproducible; bump the
  pin manually before GitHub retires the image. Pin the JDK
  (Temurin 25, matching `jvmToolchain(25)` and `gradle/gradle-daemon-jvm.properties`), the Android
  SDK/command-line-tools and build-tools versions, and cache Gradle so builds are
  reproducible. Instrumented tests run on an emulator (e.g. `reactivecircus/android-emulator-runner`) or a
  Gradle Managed Device.
- **Quality/coverage:** **SonarCloud** (DEFRA organisation) is the source of truth for coverage and the
  quality gate. Report coverage with **Kover** on its **JaCoCo engine** (≥0.8.12), which filters the
  Compose-compiler synthetic branches the default IntelliJ engine counts against the gate.
- **Dependencies:** **Gradle version catalog** for app dependencies; **Bundler** (`Gemfile`) to pin
  Fastlane and its plugins.

### Play App Signing (fixed)

**Play App Signing** manages the **app signing key** on Google's infrastructure; the repository/CI holds
only an **upload key**. This separation means a lost/compromised upload key can be reset without losing the
ability to ship updates. The upload key (base64-encoded `.jks`) is a protected, Environment-scoped secret,
decoded into a temporary keystore during the release job and never committed. Recording this as an ADR is
required.

## Branching & release model — trunk-based, tag-driven (no release branches)

This is a **small team practising trunk-based development**. The model is deliberately minimal:

- **`main` is the trunk** and is always releasable. Protect it: require PRs, green CI and review before
  merge; no direct pushes.
- **Short-lived feature branches** (`feature/*`) merge back into `main` via PR, then are deleted.
- **Releases are cut from a Git tag on `main`**, never from a long-lived branch. Tag format:
  **`v<VERSION_NAME>-BUILD_<N>`** (e.g. `v2.0.0-BUILD_19`), created only on `main` by the `release-tag`
  job of `android-ci.yml` after a green run. CI never starts a release: a person runs **Android Release**
  (`workflow_dispatch`) on the tag.
- **Hotfixes** are a normal fix on `main` (which raises `VERSION_CODE`, so a new tag) released the same way.
  Because the trunk is always releasable, there is no separate hotfix branch to maintain.
- **Release branches are NOT used and MUST NOT be introduced** for this app. They only earn their keep
  when a release must be hardened/stabilised while `main` keeps moving, or when several past versions are
  supported in parallel — neither applies to a single-team, single-live-version app. Tag-driven releases
  give a clean, auditable release point without the merge overhead. If a future need for release branches
  is identified, raise it as an ADR + governance discussion first; do not add them ad hoc.

## Versioning

- **Marketing version** (`versionName`, e.g. `2.0.0`) and the **build number N** (`VERSION_CODE`) live in
  [`version.properties`](../../version.properties), the single source read by Gradle, the shell scripts and
  Fastlane (`CrRelease::Version`). Every non-Dependabot PR must raise `VERSION_CODE` (it never resets) and
  must not lower `VERSION_NAME`; `check-version-bump.sh` enforces this.
- **`N` is 1..9999.** The versionCode actually **uploaded** is `channel × 10 000 + N` (ADR 0022): channel 2
  for internal tracks, 1 for closed tracks (the Dev app uses 1). Example for N = 15: dev 10015, test 20015,
  perf-test 10015, uat 20015, prep-prod 10015; `production` promotes prep-prod's code. Within one app the
  internal channel is always higher than the closed one.
  - Gradle compiles the base bundle with `versionCode = N`; it is never uploaded. The repackaging step
    rewrites it per environment (ADR 0023), so app code must **never** read `BuildConfig.VERSION_CODE` or
    `BuildConfig.APPLICATION_ID` (a unit test guards this).
  - **Do not query the Google Play Developer API to choose a version** (race conditions, and credentials in a
    step that does not need them). The Play API is read only to make a release idempotent (is this code
    already on the track?).
  - **Commit SHA is traceability, not the version code.** The short Git SHA is embedded as the read-only
    `GIT_COMMIT_SHA` `BuildConfig` field only.
- **Never** edit a versionCode by hand and never reuse a value; each upload must be unique and higher than
  the previous one in that app.

## Configuration strategy & compile-once packaging (frozen — ADR 0023)

**Frozen decision:** **compile once, package per environment.** There are **no product flavors**: one
`compile` job builds a single **unsigned, config-free** `bundleRelease` (applicationId
`mmo.catchrecording.android`), and each environment job repackages that same bundle with the
`release-tools` CLI (`inspect` / `package` / `verify`), which:

- sets the manifest `package` (and every attribute value equal to, or prefixed by, the old package, plus the
  resource-table package) to that app's package, sets the versionCode, and injects
  `base/assets/mmocr_app_config.json` with that environment's validated configuration;
- then the lane signs with the upload key, checks the signer certificate against `UPLOAD_CERT_SHA256`, and
  `verify` proves every other entry is byte-identical to the base (plus a bundletool validate).
- **Environment configuration** comes from `CR_APP_CFG_*` GitHub Environment **variables**, validated against
  `config/app-config.schema.json` by `CrRelease::AppConfig`. Never a secret: anything in the app can be read.
- **No runtime endpoint selector.** The base bundle is not encrypted (it holds no config or secrets); this
  deviation from iOS is recorded in ADR 0023. If Play rejects a renamed package, the fallback is one compile
  per app.

## Application identity & Play app model (frozen — ADR 0022)

**Three Google Play apps** (separate packages, installable side by side), **six environments**:

| Env | Package | Managed Publishing | Track |
|---|---|---|---|
| dev | `mmo.catchrecording.dev.android` | OFF | internal |
| test | `mmo.catchrecording.test.android` | OFF | internal |
| perf-test | `mmo.catchrecording.test.android` | OFF | closed `perf-test` |
| uat | `mmo.catchrecording.android` | ON | internal |
| prep-prod | `mmo.catchrecording.android` | ON | closed `prep-prod` |
| production | `mmo.catchrecording.android` | ON | `production` (promoted from `prep-prod`) |

- **Managed Publishing is ON only in the Prod app.** Only the Prod app is ever released to **production**.
- The debug build installs as `mmo.catchrecording.android.debug`.
- Each app has its own Play App Signing registration; the versionCode scheme above keeps tracks distinct.
- Track names `perf-test` and `prep-prod` are created by hand and must match exactly.

## Release management (development → production)

The flow from a developer's change to a production Google Play release:

```
short-lived feature branch  ──PR──▶  main (trunk, always releasable)
  │  PR CI: ktlint/detekt/lint · build · unit/UI tests + coverage (Kover) · SonarCloud PR analysis
  │  GitHub-native gates: CodeQL · Dependabot · secret scanning + push protection
  ▼
main CI: full tests + SonarCloud main analysis  ──▶  release-tag job creates  v<VERSION_NAME>-BUILD_<N>
  ▼
Android Release (workflow_dispatch on the tag; inputs dev / test / prod + distribution manual|play)
  compile (no gate)  one unsigned, config-free base bundle
  ├─ dev        [env: dev — no gate]      Dev app · internal
  ├─ test       [env: test — gate]        Test app · internal
  ├─ perf-test  [env: perf-test — gate]   Test app · closed track perf-test
  ├─ uat        [env: uat — gate]         Prod app · internal
  ├─ prep-prod  [env: prep-prod — gate]   Prod app · closed track prep-prod → review → Publish changes → sanity
  └─ production [env: production — gate]  PROMOTE prep-prod's versionCode → production → review → timed Publish changes
  ▼
monitor (Play Console vitals + crash reporting)  ──▶  hotfix = fix on main + next build number
```

### GitHub Environments & approval gates

Define **six** GitHub Environments, one per environment job. A GitHub Environment approval gates the
**start of a job**. Names match the environment keys.

| Environment | Job | Approval |
|-------------|-----|----------|
| `dev` | `dev` | None |
| `test` | `test` | **Required reviewers**; prevent self-review |
| `perf-test` | `perf-test` | **Required reviewers**; prevent self-review |
| `uat` | `uat` | **Required reviewers**; prevent self-review |
| `prep-prod` | `prep-prod` | **Required reviewers**; prevent self-review |
| `production` | `production` | **Required business/release reviewers**; prevent self-review |

- The chain is `compile → dev → test → perf-test → uat → prep-prod → production`. `perf-test` runs only if
  `test` succeeded, `prep-prod` only if `uat` succeeded and `production` only if `prep-prod` succeeded; the
  `dev`/`test`/`prod` inputs skip a whole app's jobs. There is **no go-live job**.
- Scope release secrets to the Environment that uses them, not the repo, so they are exposed only after that
  stage's approval. Do not mix SonarCloud credentials with signing/release credentials.
- Restrict every Environment's deployments to **`v*` tags**. Keep workflow `permissions:` least-privilege
  (`contents: read`) even after approval, and `concurrency` with `cancel-in-progress: false`.
- Never interpolate workflow inputs directly into `run:`; pass them through `env:`.

### Distribution

- **`play` (default):** the job uploads through the Play Developer API (`CrRelease::PlayPublisher`);
  `production` promotes the code already on `prep-prod`.
- **`manual`:** the job produces the signed, verified AAB as a 7-day workflow artifact and prints
  the Play Console steps (upload to which app and track). Required for the **first upload of each new Play
  app** (the API cannot create it) and a fallback whenever API access is unavailable.
- **Review safety:** non-internal commits use `changesInReviewBehavior: ERROR_IF_IN_REVIEW` so a commit never
  restarts a review already in progress; internal uploads never enter review. Re-runs are idempotent: a stage
  already on its track is skipped, an uploaded-but-unreleased bundle is released without re-upload, and a
  full re-run's `compile` reuses the run's `release-base` artifact. Resume with *Re-run failed jobs*.
- **Base provenance:** `compile` records the commit, tag and base SHA-256 (`base-provenance.json` and the
  job output `base_sha256`); downloads use `digest-mismatch: error`; every environment job and every reuse
  re-hashes the base and fails on any mismatch (ADR 0023).
- **Prep-prod → sanity → promote → timed Publish.** Prod changes wait for a person: after review the release
  manager clicks **Publish changes** in Play Console (the API cannot) for `prep-prod`, sanity testers sign
  off, then `production` promotes the same code, and **Publish changes** is clicked again at the agreed
  go-live time. Production release notes are in `fastlane/release-notes/production/en-GB.txt` (optional
  `cy.txt`), at most 500 characters each.
- Provide tester-friendly release notes for the testing tracks ("what to test", "known limitations").

## Security gates — native GitHub features vs CI workflow stages

Be precise about *where* each control lives. **Do not turn a native feature into a hand-rolled CI stage
unless a separate workflow is explicitly required.**

**GitHub-native (configured in repo Settings or a config file — not part of the Fastlane build/test/release
workflows):**

- **CodeQL (SAST).** CodeQL supports Kotlin (`java-kotlin`). Two options:
  - **Default setup** — enabled in *Settings → Code security → Code scanning*; GitHub manages the run. No
    hand-written workflow.
  - **Advanced setup** — a dedicated **`.github/workflows/codeql.yml`** workflow you maintain. Use this
    when you need control over the Gradle build, the query suite, triggers or the runner. **This repo
    maintains CodeQL as its own separate advanced-setup workflow** (see the release-pipeline skill), so the
    Kotlin/Gradle build step is explicit and reproducible. Keep it in its **own** workflow file, separate
    from the PR-CI and release workflows.
- **Dependabot.** Configured via **`.github/dependabot.yml`** (a native config file — *not* a GitHub
  Actions workflow). Maintain it as its **own separate file**, covering the `github-actions`, `gradle` and
  `bundler` ecosystems. Optionally pair it with a small auto-merge workflow for patch/minor security
  updates, but the update mechanism itself is `dependabot.yml`.
- **Secret scanning + push protection.** Enabled in *Settings → Code security*. Native — no workflow. If a
  secret is ever exposed, follow DEFRA's
  [credential exposure](https://defra.github.io/software-development-standards/processes/credential_exposure/)
  process immediately.

**CI workflow stages (GitHub Actions files you author):**

- **PR CI** (`.github/workflows/android-ci.yml`) — ktlint/detekt/Android Lint, build, unit/UI tests with
  coverage (Kover), then the **SonarCloud** scan. Runs on pull requests and pushes to `main`.
- **Release** (`.github/workflows/android-release.yml`) — `workflow_dispatch` on a `v*` tag; runs the
  Fastlane `compile_release`, `release_environment` and `promote_production` lanes behind the gated
  Environments above.
- **CodeQL** (`.github/workflows/codeql.yml`) — the separate advanced-setup SAST workflow described above.

**MobSF binary (AAB/APK) scanning** is **not required** for the baseline: SonarCloud + CodeQL + Dependabot +
secret scanning already give strong coverage for a small team. Treat MobSF as an **optional later maturity
step**; if adopted, run it against the release AAB before the production gate.

## Code signing

- **Do not commit keystores, upload keys, `.jks`/`.keystore`, service-account JSON or passwords** to the
  repository.
- The signing approach is a decision to be **researched, recommended in the agent's plan and recorded as an
  ADR**. The frozen baseline is **Play App Signing with a separate upload key**:
  1. **Play App Signing (required)** — Google generates/holds the **app signing key**; you hold an
     **upload key**. A lost/compromised upload key can be reset in the Play Console without losing update
     capability.
  2. **Upload-key custody in CI** — the upload keystore is base64-encoded and stored as a protected,
     Environment-scoped GitHub secret, decoded into a **temporary keystore** during the release job and
     deleted at job end (`cleanup-signing-assets.sh`, `if: always()`). Base64 is encoding only —
     protection relies on GitHub secret storage and access policy.
  3. **Two upload keys, by app tier** — a **non-prod** upload key for the Dev and Test apps (Environments
     `dev`, `test`, `perf-test`) and the **Prod** upload key for the Prod app (`uat`, `prep-prod`). The
     Prod key must not be present in `dev`. Each Environment holds the cert fingerprint as the variable
     `UPLOAD_CERT_SHA256`, which the lane checks against the signed bundle.
- Always:
  - Use a **Google Play Developer API service-account JSON key** for uploads and promotion — not an
    interactive Google login. Signing (upload key) and Play API authentication are **separate concerns**;
    the service account does not replace the upload key.
  - **Accepted deviation DA-1 (ADR 0022):** a **single** service account holds release permissions on all
    three apps and is stored only in GitHub Environments (all environment jobs, including `production`).
    Mitigations: tags only from protected `main`, reviewer gates, CODEOWNERS on `.github/workflows/` and
    `fastlane/` recommended.
  - Rotate the key periodically; monitor upload-key and service-account validity; document rotation,
    revocation and recovery procedures.

## Secrets management

- **Never commit secrets.** Store all release secrets as **GitHub Actions encrypted secrets scoped to the
  gated Environments** (`dev` / `test` / `perf-test` / `uat` / `prep-prod` / `production`) — each
  exposing only the credentials it needs.
- Per packaging Environment (`dev`, `test`, `perf-test`, `uat`, `prep-prod`): secrets
  `UPLOAD_KEYSTORE_BASE64`, `UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_ALIAS`, `UPLOAD_KEY_PASSWORD` and
  `PLAY_SERVICE_ACCOUNT_JSON`; variables `CR_APP_CFG_API_BASE_URL`, `CR_APP_CFG_USE_STUB_AUTH`,
  `CR_APP_CFG_USE_FAKE_SIGN_IN`, `CR_APP_CFG_MSAL_CLIENT_ID`, `CR_APP_CFG_MSAL_AUTHORITY_URL` (optional
  `CR_APP_CFG_MSAL_REDIRECT_URI`) and
  `UPLOAD_CERT_SHA256`. `production` needs only `PLAY_SERVICE_ACCOUNT_JSON`. `SONAR_TOKEN` is a repository
  secret for CI only.
- Environment configuration values are **variables, never secrets**, and Environment-level only; they are
  validated against `config/app-config.schema.json`. Sensitive values are never packaged into the app.
- **Never print secrets to logs.** Do not echo keystores, key aliases, service-account JSON or keystore
  contents. Rely on GitHub's masking and keep `set -x` away from secret-bearing steps.
- **Pin every third-party GitHub Action to a full commit SHA** (a DEFRA supply-chain requirement) and set
  least-privilege `permissions:` on every workflow. Enable Dependabot, CodeQL, secret scanning, push
  protection and dependency review; gate workflow/release-tooling changes behind CODEOWNERS or equivalent
  protected review.

## Reliability & reproducibility

- Pin the runner image, JDK version, Android SDK/build-tools, Gradle (via the wrapper) and Fastlane (via
  `Gemfile.lock`). Cache Gradle dependencies (only where cache keys prevent unsafe cross-context
  restoration). Consider Gradle configuration cache.
- Use `concurrency:` groups to cancel superseded PR runs but **never** cancel an in-flight release run.
- Every release must be traceable end to end: **release tag → commit SHA → workflow run (step summary:
  package, versionCode, base and package SHA-256) → Play Console build → track & sign-off → production
  release**.

## Real-device testing & data residency (optional maturity step)

Emulator/Gradle Managed Device suites in CI cover broad automation; a cloud real-device service (e.g.
**Firebase Test Lab** or **BrowserStack App Automate**) may supplement — not replace — emulators and human
UAT for hardware, OS-version, network, offline and compatibility scenarios. It is **subject to procurement
and security approval**. Because a real cloud device processes app data **in memory** even though nothing is
persisted there and the backend stays **UK-hosted**, the **cloud devices/region used must be UK-located** to
satisfy data-residency requirements. Record adoption and the residency constraint as an **ADR**.

## Architecture decision records (create/update before privileged automation)

Record at least these as ADRs under `docs/adr/`:

1. GitHub Actions + Fastlane as the Android delivery architecture (and the native-app exception).
2. **Packaged app configuration** — ADR 0024 (no flavors; amended by 0023).
3. **Three-app release topology** — ADR 0022 (three Play apps, six environments, Managed Publishing on the
   Prod app only, versioning, prep-prod → production promotion, DA-1).
4. **Compile once, package per environment** — ADR 0023.
5. Code-signing strategy and signing-asset custody (Play App Signing + upload keys) — ADR 0025 items 5–6,
   extended by 0022.
6. Cloud real-device testing platform and UK data residency, if adopted.

## Definition of Done (pipeline changes)

- [ ] Workflow YAML is valid, least-privilege (`permissions:`), and pins Actions (full commit SHA) + tool versions
- [ ] Secrets are Environment-scoped per stage, never committed, never logged; workflow inputs never interpolated into `run:`
- [ ] Trunk-based/tag-driven model preserved — no release branch introduced; tags created on `main` only
- [ ] Version from `version.properties` (`N` ≤ 9999); uploaded code = `channel × 10 000 + N`; no Play API query to choose a version; `GIT_COMMIT_SHA` is traceability only
- [ ] The gated Environments (`test`, `perf-test`, `uat`, `prep-prod`, `production`) remain gated by required reviewers with self-review prevented (`dev` is ungated); all deploy only from `v*` tags
- [ ] One compiled base repackaged per environment; `release-tools verify` and the `UPLOAD_CERT_SHA256` check pass; app code does not use `BuildConfig.APPLICATION_ID` / `VERSION_CODE`
- [ ] Non-internal Play commits use `ERROR_IF_IN_REVIEW`; `production` promotes the `prep-prod` code and never uploads
- [ ] SonarCloud quality gate wired and passing; coverage (Kover) reported
- [ ] CodeQL and Dependabot maintained as their own separate files
- [ ] Signing uses Play App Signing + the right upload key per app tier + a Play service-account key + a temporary keystore; assets never committed
- [ ] Topology, packaging and signing decisions recorded as ADRs (0022, 0023); README *Release* section updated for any new pipeline, secret, environment or signing decision
- [ ] No iOS tooling added to this Android-only repo
