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
- **Deployment engine:** **Fastlane** (`supply` / `upload_to_play_store`) — chosen to keep one uniform
  automation model across the Android and (separately-hosted) iOS apps. It is the Play release toolkit,
  **not** a second orchestrator. Build, version and upload to Google Play tracks are driven by Fastlane
  lanes (the AAB itself is built by Gradle). Do **not** introduce a second deployment mechanism.
- **Build system:** **Gradle (Kotlin DSL)** with a version catalog (`gradle/libs.versions.toml`). Build an
  **Android App Bundle (AAB)** for release (`bundleRelease`), never a bare APK for Play upload.
- **Build infrastructure:** **GitHub-hosted Ubuntu runners** (e.g. `ubuntu-latest`). Pin the JDK
  (Temurin 17), the Android SDK/command-line-tools and build-tools versions, and cache Gradle so builds are
  reproducible. Instrumented tests run on an emulator (e.g. `reactivecircus/android-emulator-runner`) or a
  Gradle Managed Device.
- **Quality/coverage:** **SonarCloud** (DEFRA organisation) is the source of truth for coverage and the
  quality gate. Report coverage with **Kover**.
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
  **`android-vMAJOR.MINOR.PATCH`** (e.g. `android-v1.4.0`). Pushing a matching tag triggers the release
  workflow.
- **Hotfixes** are a normal fix on `main` plus a **new higher patch tag** (e.g. `android-v1.4.1`). Because
  the trunk is always releasable, there is no separate hotfix branch to maintain.
- **Release branches are NOT used and MUST NOT be introduced** for this app. They only earn their keep
  when a release must be hardened/stabilised while `main` keeps moving, or when several past versions are
  supported in parallel — neither applies to a single-team, single-live-version app. Tag-driven releases
  give a clean, auditable release point without the merge overhead. If a future need for release branches
  is identified, raise it as an ADR + governance discussion first; do not add them ad hoc.

## Versioning

- **Marketing version** (`versionName`, e.g. `1.4.0`) is derived from the release **tag**
  (`android-v1.4.0` → `1.4.0`). It is the single human-facing SemVer.
- **`versionCode`** (a monotonically increasing integer) is derived **deterministically** from the
  **release workflow's** `GITHUB_RUN_NUMBER` (which increments by one on every release run). **Do not query
  the Google Play Developer API for the latest version code** — a network lookup adds a race condition
  between concurrent releases and pulls release credentials into a step that does not need them. The CI run
  counter avoids all three and keeps versioning fully derivable from the tagged commit.
  - **The one rule that must hold:** each uploaded `versionCode` must be **unique and higher** than the
    previous upload. The run number satisfies this for normal serialised releases. If the release workflow
    is ever reset/replaced such that the run number could regress, apply a documented one-off offset — do
    **not** reintroduce a Play API lookup.
  - **Commit SHA is traceability, not the version code.** Embed the short Git SHA (and the tag) as a
    read-only `BuildConfig` field (e.g. `GIT_COMMIT_SHA`) for traceability only. A SHA is hexadecimal and
    non-monotonic, so it can never serve as `versionCode` (which must be an increasing integer).
- Gradle sets both at build time from the injected values (e.g. Gradle properties/`-P` args); do not
  hard-code release version numbers in `build.gradle.kts`. **Never** edit the version code by hand and never
  reuse a value.

## Configuration strategy & build-per-environment promotion (frozen)

**Frozen decision:** the app ships as **three separate applications**, one per environment, each with its
own `applicationId`, Google Play app record and testing tracks. Configuration is resolved at **build time
(Option B)** via **Gradle product flavors** — there is **no runtime endpoint selector**.

- **Build-time configuration (Option B).** Each environment is compiled as its own **product flavor**
  (`dev`/`test`/`prod`) with its own `applicationId`, `BuildConfig`/resource values and endpoints,
  producing a **distinct AAB per environment**. The release job selects the flavor so the **tagged commit
  is built unchanged**.
- **Build-per-environment promotion.** Because the three apps have distinct `applicationId`s (immutable
  once uploaded), a single artefact cannot move between environments. Instead **promote the commit, not the
  binary**: one release tag builds all three apps from the **same commit**. Equivalence is evidenced by the
  same commit SHA, pinned JDK/SDK/Gradle/Fastlane/runner image, and locked Gradle/Bundler dependencies — and
  by the Prod app running its own internal (and, where required, closed) testing pass before production
  rollout.
- **Track promotion is a no-rebuild operation.** Promoting an already-uploaded build to another track of
  the **same** Play app (`track_promote_to`) is a Google Play metadata action, so testers/users get the
  exact binary that passed the previous track for that environment.

**Current repo state (must be reconciled):** the app has **no configuration mechanism yet** — flavors,
`applicationId` suffixes, network security config and CI-derived versioning must be added. The target
identities are `mmo.catchrecording.android.dev` / `mmo.catchrecording.android.test` /
`mmo.catchrecording.android`.

## Application identity & applicationId model (frozen)

**Three application identities** — three Google Play app records, each with its own testing tracks:

```
mmo.catchrecording.android.dev     # Dev  — internal testing track
mmo.catchrecording.android.test    # Test — internal + closed testing (business UAT)
mmo.catchrecording.android         # Prod — internal + closed + production (staged rollout)
```

- Each environment installs **side by side** on one device (distinct `applicationId`s, via
  `applicationIdSuffix`).
- Only the **Prod** app is ever promoted to the **production** track; **Dev** and **Test** are
  testing-track-only Play app records.
- Each app has its **own** upload key registration under Play App Signing and an **independent versionCode
  namespace**.
- UAT runs on the **Test** identity against test services; the **Prod** identity gets its own internal +
  closed pass before production rollout.

## Release management (development → production)

The flow from a developer's change to a production Google Play release:

```
short-lived feature branch  ──PR──▶  main (trunk, always releasable)
  │  PR CI: ktlint/detekt/lint · build · unit/UI tests + coverage (Kover) · SonarCloud PR analysis
  │  GitHub-native gates: CodeQL · Dependabot · secret scanning + push protection
  ▼
main CI: full tests + SonarCloud main analysis
  ▼
tag  android-vX.Y.Z  ──▶  single release workflow (Fastlane); one run, six sequential gated jobs
  ├─ dev-build-internal      [env: dev — no gate]  build Dev AAB → Dev internal track
  ├─ test-build-internal     [env: test — APPROVAL A]  build Test AAB → Test internal track
  ├─ test-promote-closed     [env: test-closed — APPROVAL B]  promote SAME Test build → closed UAT track (no rebuild)
  ├─ prod-build-internal     [env: prod — APPROVAL C]  build Prod AAB → Prod internal track
  ├─ prod-promote-closed     [env: prod-closed — APPROVAL D]  promote SAME Prod build → closed track (no rebuild)
  └─ prod-promote-production [env: prod-production — APPROVAL E]  promote SAME Prod build → production (staged rollout)
  ▼
monitor (Play Console vitals + crash reporting)  ──▶  hotfix = fix on main + higher patch tag
```

### GitHub Environments & approval gates

Define **six** governed GitHub Environments — one per gated job in the single release workflow. A GitHub
Environment approval gates the **start of a job**, so each distinct manual approval is its own job /
environment. Name them for the delivery **stage**, not for physical infrastructure. A stage is reached
only once the preceding gate is approved, enforcing the promotion order.

| Environment | Purpose | Job | Approval |
|-------------|---------|-----|----------|
| `dev` | Build the **Dev** app AAB and upload to its **internal** track | `dev-build-internal` | None (auto on tag) |
| `test` | Build the **Test** app AAB and upload to its **internal** track | `test-build-internal` | **Required reviewer** (A); prevent self-approval |
| `test-closed` | Promote the **same** Test build to the **closed** UAT track (no rebuild) | `test-promote-closed` | **Separate required reviewer** (B) |
| `prod` | Build the **Prod** app AAB and upload to its **internal** track | `prod-build-internal` | **Required reviewer** (C); prevent self-approval |
| `prod-closed` | Promote the **same** Prod build to the **closed** track (no rebuild) | `prod-promote-closed` | **Separate required reviewer** (D) |
| `prod-production` | Promote the **same** Prod build to the **production** track (staged rollout) | `prod-promote-production` | **Required business/release reviewer** (E); prevent self-approval + admin bypass |

- Scope each stage's release secrets to its **own** Environment, not the repo, so they are only exposed
  after that stage's approval. Do not mix SonarCloud credentials with signing/release credentials.
- Restrict all six Environments' deployments to `main` and the `android-v*` tags. Keep workflow
  `permissions:` least-privilege even after environment approval.
- **Use staged (percentage) rollout** for the production track; monitor crash-free rate and Play vitals
  before completing the roll-out. Keep the ability to halt the staged rollout.

### Distribution

- **Google Play testing tracks** for pre-production, across **two distinct gates**: the **internal** track
  first (fast release-candidate smoke test — up to 100 internal testers, near-instant availability), then a
  **closed** testing track (business UAT — invited testers/Google Groups). Provide tester-friendly release
  notes ("what to test", "known limitations", environment details, feedback channel).
- **Production track** for release: promote the **Prod app's** track-verified build to production via
  `upload_to_play_store` / `track_promote_to`, released as a **staged rollout**. Uploading a build to a
  track and promoting it to production are **separate actions** — model them separately in automation and
  runbooks.

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
- **Release** (`.github/workflows/android-release.yml`) — tag/`workflow_dispatch`-triggered; runs the
  Fastlane `beta`/`release` lanes behind the gated Environments above.
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
     deleted at job end. Restrict who can read/rotate it. Base64 is encoding only — protection relies on
     GitHub secret storage and access policy.
- Whichever is chosen:
  - Use a **Google Play Developer API service-account JSON key** (`json_key_data`) for uploads — not an
    interactive Google login. Signing (upload key) and Play API authentication are **separate concerns**;
    the service account does not replace the upload key.
  - In CI, materialise signing assets into a **temporary keystore** deleted at the end of the job.
  - Use a **least-privilege** service account (only the releases/tracks it needs); rotate the key
    periodically; monitor upload-key and service-account validity; document rotation, revocation and
    recovery procedures.

## Secrets management

- **Never commit secrets.** Store all release secrets as **GitHub Actions encrypted secrets scoped to the
  gated Environments** (`dev` / `test` / `test-closed` / `prod` / `prod-closed` / `prod-production`) —
  each stage exposing only the credentials it needs (per-app upload key and per-app Play service account are
  kept separate).
- Typical secrets: `PLAY_SERVICE_ACCOUNT_JSON` (per Play app), `UPLOAD_KEYSTORE_BASE64`,
  `UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_ALIAS`, `UPLOAD_KEY_PASSWORD`; plus `SONAR_TOKEN`.
- Non-sensitive build configuration belongs in **Gradle** (flavors, `BuildConfig`, resources) committed to
  the repo; inject sensitive values at build time from CI (Gradle properties / the
  [secrets Gradle plugin](https://github.com/google/secrets-gradle-plugin)). Document every config key in
  Gradle and the README.
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
- Every release must be traceable end to end: **release tag → commit SHA → workflow run → versionName →
  versionCode → AAB checksums → Play Console build → tracks & sign-off → production version & rollout
  status**.

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
2. **Build-time configuration & environment promotion** (Option B; three environments compiled from one
   commit via product flavors) — supersedes any runtime-configuration option.
3. Code-signing strategy and signing-asset custody (Play App Signing + per-app upload keys).
4. **Three-application applicationId and environment model** (`dev` / `test` / `prod` as separate Play
   apps).
5. **Single-workflow, six-environment release topology** (per-stage and per-track-promotion gates).
6. **Build-per-environment promotion & commit-equivalence policy** — supersedes build-once-and-promote.
7. Internal & closed testing-track distribution model (per-app internal + closed tracks).
8. Cloud real-device testing platform and UK data residency, if adopted.
9. Production approval and staged-rollout policy.

## Definition of Done (pipeline changes)

- [ ] Workflow YAML is valid, least-privilege (`permissions:`), and pins Actions (full commit SHA) + tool versions
- [ ] Secrets are Environment-scoped per stage, never committed, never logged
- [ ] Trunk-based/tag-driven model preserved — no release branch introduced
- [ ] `versionName` derives from the tag; `versionCode` from the **release** `GITHUB_RUN_NUMBER` (no Play API query); `GIT_COMMIT_SHA` embedded as `BuildConfig` traceability metadata
- [ ] The gated Environments (`test`, `test-closed`, `prod`, `prod-closed`, `prod-production`) remain gated by manual approval (`dev` is ungated), with self-approval prevented where supported
- [ ] Build-per-environment from one tested commit; track promotion is a no-rebuild Play operation; commit-equivalence (same SHA, pinned toolchain, locked deps) evidenced
- [ ] SonarCloud quality gate wired and passing; coverage (Kover) reported
- [ ] CodeQL and Dependabot maintained as their own separate files
- [ ] Signing uses Play App Signing + a Play service-account key + a temporary keystore; assets never committed
- [ ] Configuration-strategy, applicationId and signing decisions recorded as ADRs; README updated for any new pipeline, secret, environment or signing decision
- [ ] No iOS tooling added to this Android-only repo
