# MMO Catch Recording (Android)

Native Android app for the Marine Management Organisation (MMO) Catch Recording service, built for DEFRA.
See [`docs/adr/0001-native-android-app-exception.md`](docs/adr/0001-native-android-app-exception.md) for
why this is a native app (a governed exception to the DEFRA mobile standard's default guidance).

## Quick start

Prerequisites: JDK 25 (Temurin 25.0.5 or later), Android Studio (latest stable), an Android device/emulator running API 26+.
Fastlane (optional locally, used by CI) additionally needs Ruby 3.3+ and Bundler.

```powershell
git clone <this-repo-url>
cd mmo-cr-android

# Point debug builds at a backend (git-ignored; see docs/development/reference-data.md)
#   cp local.properties.example local.properties   (git-ignored; every crAppCfg.* key is explained in it)

# Build
./gradlew assembleDebug

# Run unit tests
./gradlew testDebugUnitTest

# Run unit tests + generate coverage report (Kover)
./gradlew testDebugUnitTest koverXmlReportDebug

# Lint / static analysis
./gradlew ktlintCheck detekt lintDebug --continue

# Regenerate the offline map data (also runs automatically before assembleDebug/Release)
./gradlew :app:generateDebugMapData

# Install & run on a connected device/emulator
./gradlew installDebug
```

There are no build flavors: the backend comes from a packaged app-config asset ([ADR
0024](docs/adr/0024-packaged-app-configuration.md)) — see
[`docs/development/reference-data.md`](docs/development/reference-data.md) for which backend to pick and
how to stand one up. The debug app installs as `mmo.catchrecording.android.debug`, beside the Play build.

The same steps are also exposed as Fastlane lanes, which is exactly what CI runs:

```powershell
bundle install
bundle exec fastlane lint              # ktlintCheck + detekt + lintDebug (--continue)
bundle exec fastlane build             # assembleDebug
bundle exec fastlane test              # testDebugUnitTest + koverXmlReportDebug + app-config plugin tests
bundle exec fastlane instrumented_test # connectedDebugAndroidTest
```

Or open the project in Android Studio and use the standard Run/Debug configurations.

## Project structure

```
app/src/main/java/uk/gov/defra/mmocatchrecord/
├── common/
│   ├── design/        # GOV.UK design tokens & theme (MmoTheme, Color, Spacing, Typography)
│   └── navigation/     # Type-safe Destination/Route model + MmoNavHost
├── core/
│   ├── architecture/   # BaseViewModel, ViewState/UiStatus, ValidationHelper (MVVM + Clean Architecture)
│   ├── security/       # Biometric/session interfaces + Stage-1 fakes (real Keystore impl is a later stage)
│   └── root/           # RootPhase, SessionCoordinator, RootNavigation, MainActivity
├── di/                 # Hilt modules
└── MmoApplication.kt   # Application class, @HiltAndroidApp entry point
```

Feature work (once past this foundational stage) should follow the same `domain` / `data` / `presentation`
split per feature package, per
[`docs/adr/0002-mvvm-clean-architecture.md`](docs/adr/0002-mvvm-clean-architecture.md).

## Architecture & key decisions

- **MVVM + Clean Architecture** — see ADR 0002.
- **Room** for offline-first local persistence (Stage 1: compile-time stub only) — see ADR 0003.
- **Hilt** for dependency injection (the copilot-instructions §5 default) — see ADR 0005 (supersedes ADR
  0004, which recorded a since-reverted Koin deviation).
- **Navigation Compose** (stable) for in-app navigation.
- **Material 3, light-only GOV.UK theme** — no dark mode, no dynamic colour, system-font fallback (GDS
  Transport is not licensed for non-gov.uk products, so it is not bundled; the font is swapped in one
  place, `common/design/Typography.kt`, if a licensed alternative becomes available).
- **Security** — `androidx.biometric` is wired as a dependency; biometric prompt/preference remain Stage-1
  interfaces and in-memory fakes. Data at rest is protected with **non-auth-bound Android Keystore AES-GCM**
  keys (`core/security/KeystoreAesGcmCipher`) — the SQLCipher passphrase (ADR 0006) and the encrypted
  session timestamps behind `core/security/SessionStore` (ADR 0019). **Neither Tink nor
  `androidx.security-crypto` is used**: the latter is deprecated upstream and was removed. Never log
  biometric/session state or PII — see the KDoc on each interface in `core/security/`.
- **Offline fisheries statistical sub-rectangle map** — a fully offline, custom Compose `Canvas`-rendered
  map (no map SDK/tiles/API key/network) with a build-time GeoJSON preprocessing task in the `build-logic/`
  composite Gradle build — see ADR 0013 and [`docs/development/offline-map.md`](docs/development/offline-map.md).
- **Reference-data API integration** — Retrofit/OkHttp-backed, cache-first (Room) with a bundled offline
  fallback; the backend comes from the packaged app config — see ADR 0015/0016/0024 and
  [`docs/development/reference-data.md`](docs/development/reference-data.md) for the developer runbook.
- Full list of ADRs: [`docs/adr/`](docs/adr/).

## Developer guidelines

- **Naming**: packages lower-case, `PascalCase` for classes/composables, `camelCase` for functions/vals.
  Composables are `PascalCase` nouns describing what they render (e.g. `SignInScreen`), not verbs.
- **Patterns**: keep composables small and stateless; hoist state to ViewModels; ViewModels expose a
  single immutable `StateFlow<UiState>` and accept UDF events via `dispatch(event)`; no business logic in
  Composables; no Android framework types in `domain`.
- **Error handling**: model errors as sealed hierarchies, never swallow silently, distinguish
  transient/retryable (offline, network) from terminal (validation, auth) failures, and never leave a
  screen on an endless spinner without an accessible error/retry state.
- **Testing**: JUnit4 + Mockito/mockito-kotlin + kotlinx-coroutines-test + Compose UI Test + Espresso.
  Ship tests with the code that introduces or changes behaviour — see
  [`docs/adr`](docs/adr) and the repo's testing instructions for coverage targets (≥90% global, ≥95% core
  business logic, 100% error-handling/security-critical paths).
- **Accessibility**: WCAG 2.2 AA is a legal requirement — TalkBack labels/roles, 200% font scaling, 4.5:1
  contrast, 48×48dp touch targets, visible focus indicators (see `Modifier.govukFocusIndicator()` in
  `common/design/Theme.kt`), reduced motion, and never colour alone to convey meaning.
- **Lint/format**: `ktlintCheck` and `detekt` must pass with zero warnings before merge; CI enforces this.

## ADR index

| ADR | Title |
|-----|-------|
| [0001](docs/adr/0001-native-android-app-exception.md) | Native Android app as a governed exception |
| [0002](docs/adr/0002-mvvm-clean-architecture.md) | MVVM + Clean Architecture |
| [0003](docs/adr/0003-room-offline-persistence.md) | Room for offline-first persistence |
| [0004](docs/adr/0004-koin-dependency-injection.md) | Koin for dependency injection (superseded) |
| [0005](docs/adr/0005-hilt-dependency-injection.md) | Hilt for dependency injection |
| [0006](docs/adr/0006-catch-record-draft-persistence-and-encryption.md) | Catch-record draft persistence and encryption |
| [0007](docs/adr/0007-wizard-navigation-and-state-model.md) | Wizard navigation and state model |
| [0008](docs/adr/0008-reference-data-stub.md) | Reference-data stub (superseded by 0016) |
| [0009](docs/adr/0009-workmanager-background-submission-sync.md) | WorkManager background submission sync |
| [0010](docs/adr/0010-catch-record-draft-migrations-and-active-draft-uniqueness.md) | Catch-record draft migrations and active-draft uniqueness |
| [0011](docs/adr/0011-structured-logging-and-safe-error-mapping.md) | Structured logging and safe error mapping |
| [0012](docs/adr/0012-app-language-preference-datastore.md) | App language preference in DataStore |
| [0013](docs/adr/0013-offline-fisheries-map-rendering.md) | Offline fisheries statistical sub-rectangle map rendering |
| [0014](docs/adr/0014-catch-record-status-audit-and-offline-sync-confirmation.md) | Catch-record status, audit timestamps and offline-sync confirmation |
| [0015](docs/adr/0015-http-client-retrofit-okhttp.md) | HTTP client: Retrofit + OkHttp |
| [0016](docs/adr/0016-reference-data-api-integration.md) | Reference-data API integration |
| [0017](docs/adr/0017-statistical-sub-area-coordinates-and-ices-rectangle-display.md) | Statistical sub-area coordinates and ICES rectangle display |
| [0018](docs/adr/0018-independent-catch-record-drafts.md) | Independent catch-record drafts |
| [0019](docs/adr/0019-entra-external-id-authentication.md) | Authentication via Microsoft Entra External ID (native auth) |
| [0020](docs/adr/0020-16kb-page-size-native-library-alignment.md) | 16 KB page-size native library alignment |
| [0021](docs/adr/0021-local-data-separation-per-backend-environment.md) | Local data separation per backend environment |
| [0022](docs/adr/0022-android-three-app-release-topology.md) | Android release topology: three Play apps, six environments |
| [0023](docs/adr/0023-android-compile-once-package-per-environment.md) | Android: compile once, package per environment |
| [0024](docs/adr/0024-packaged-app-configuration.md) | Packaged app configuration (replaces build flavors) |
| [0025](docs/adr/0025-android-release-poc.md) | Android release pipeline — POC (superseded by 0022/0023) |
| [0026](docs/adr/0026-catch-record-submission-api-integration.md) | Catch record submission API integration |
| [0000](docs/adr/0000-ios-adr-references-TBC.md) | iOS ADR references — TBC |

## CI

`.github/workflows/android-ci.yml` runs on pull requests, the merge queue and `main`: version-bump check
(PRs, except Dependabot), release tooling tests, static analysis, debug assemble, unit tests with coverage,
SonarCloud and emulator-based instrumented/accessibility tests. Every Gradle invocation goes through a
Fastlane lane, so CI and local runs are identical. After a green push to `main` (not a Dependabot merge),
its `release-tag` job publishes the release tag. CI never starts a release. In the merge queue, the
`main-idle` job fails while Android CI is still running on `main`; re-queue the PR once that run finishes.

Every third-party Action is pinned to a **full commit SHA** (a DEFRA supply-chain requirement) with the
version in a trailing comment, and `.github/dependabot.yml` keeps those pins current via the
`github-actions` ecosystem (weekly, grouped). Runners are pinned to **`ubuntu-24.04`** rather than
`ubuntu-latest` for reproducibility — that pin must be bumped **manually** before GitHub retires the image.

## Release

Design: [ADR 0022](docs/adr/0022-android-three-app-release-topology.md) (three Play apps, six
environments) and [ADR 0023](docs/adr/0023-android-compile-once-package-per-environment.md) (compile
once, package per environment). Pipeline rules: [`ci-cd.instructions.md`](.github/instructions/ci-cd.instructions.md).

### Topology

| Environment | Play app (package) | Track | Managed Publishing | Backend | Code (N = 15) |
|---|---|---|---|---|---|
| `dev` | Dev (`mmo.catchrecording.dev.android`) | internal | off | Dev | 10015 |
| `test` | Test (`mmo.catchrecording.test.android`) | internal | off | Test | 20015 |
| `perf-test` | Test | closed `perf-test` | off | Perf-Test | 10015 |
| `uat` | Prod (`mmo.catchrecording.android`) | internal | on | Ext-Test | 20015 |
| `prep-prod` | Prod | closed `prep-prod` | on | Prod | 10015 |
| `production` | Prod | `production` | on | Prod | 10015 (promoted from `prep-prod`) |

### Version scheme

[`version.properties`](version.properties) holds `VERSION_NAME` and `VERSION_CODE`, the build number **N**
(1..9999). Every non-Dependabot PR must raise N (it never resets) and must not lower `VERSION_NAME`. When
CI passes on `main`, `android-ci.yml` tags the commit `v<VERSION_NAME>-BUILD_<N>` (e.g. `v2.0.0-BUILD_19`);
tags are created on `main` only. The versionCode uploaded to Play is `channel × 10000 + N`: channel 2 for
internal tracks, 1 for closed tracks. The app must not read `BuildConfig.APPLICATION_ID` or
`BuildConfig.VERSION_CODE`, which are rewritten after compilation.

### Run a release

Actions → **Android Release** → Run workflow → *Use workflow from* → **Tags** → the release tag, then set:

| Input | Meaning |
|---|---|
| `dev`, `test`, `prod` | Which apps to release (default all). `dev` runs `dev`; `test` runs `test` and `perf-test`; `prod` runs `uat`, `prep-prod` and `production` |
| `distribution` = `manual` (default) | Each job leaves a signed, verified AAB as a 7-day artifact `aab-<env>-<tag>` and prints the Play Console steps; upload it by hand |
| `distribution` = `play` | Each job uploads through the Play Developer API; `production` promotes |

Jobs run `compile` → `dev` → `test` → `perf-test` → `uat` → `prep-prod` → `production`; each waits for its
Environment's reviewers (none for `dev`). `perf-test` needs `test`, `prep-prod` needs `uat` and
`production` needs `prep-prod` to have succeeded. The run refuses a branch, or a tag that does not match
`version.properties` at that commit. Each job writes the tag, package, versionCode and SHA-256s to the run
summary.

**Resuming a run.** A failed job (an intermittent error, or a rejected approval) is resumed with
**Re-run failed jobs** on the same run: it re-runs that job and the ones after it, and the jobs that
succeeded are not repeated. In `play` mode every stage is idempotent, so **Re-run all jobs** is safe too:

- `compile` reuses the base bundle the run already built, so every stage ships the same binary.
- Every stage re-hashes the base and refuses to continue unless it matches the hash and commit `compile`
  recorded (ADR 0023, *Base provenance*).
- A stage whose versionCode is already on its track is skipped, and the run continues to the next gate.
- A stage whose bundle an earlier attempt uploaded but never released is released without a new upload.

Re-runs are possible for 30 days after the run started. Prefer a re-run to a new run on the same tag: a new
run compiles the base again.

The lanes live in [`fastlane/lanes/release.rb`](fastlane/lanes/release.rb), with logic in
[`fastlane/lib/cr_release/`](fastlane/lib/cr_release) and the repackaging CLI in
[`build-logic/release-tools`](build-logic/release-tools):

- `compile_release` — builds one unsigned, config-free bundle and the CLI, and checks it matches the tag.
- `release_environment env:<dev|test|perf-test|uat|prep-prod> distribution:<manual|play>` — validates the
  Environment's `CR_APP_CFG_*` variables against [`config/app-config.schema.json`](config/app-config.schema.json),
  repackages the base (`package`), signs it with the upload key, checks the signer against
  `UPLOAD_CERT_SHA256`, runs `verify`, then uploads (`play`) or keeps the AAB (`manual`). Closed-track
  uploads fail rather than restart a review that is already in progress.
- `promote_production distribution:<manual|play>` — promotes the `prep-prod` code to `production`; it never
  uploads. Release notes come from `fastlane/release-notes/production/en-GB.txt` (optional `cy.txt`) at the
  release tag, at most 500 characters each (see the runbook).

### GitHub Environments

Create `dev` (no reviewers), `test`, `perf-test`, `uat`, `prep-prod` and `production` (required reviewers,
prevent self-review). Allow deployments only from `v*` tags in every one.

| Name | Kind | Environments | Value |
|---|---|---|---|
| `UPLOAD_KEYSTORE_BASE64` | Secret | `dev`, `test`, `perf-test`: non-prod upload key; `uat`, `prep-prod`: Prod upload key | Upload keystore (PKCS12), base64 on one line |
| `UPLOAD_KEYSTORE_PASSWORD` | Secret | as above | Keystore password |
| `UPLOAD_KEY_ALIAS` | Secret | as above | Key alias |
| `UPLOAD_KEY_PASSWORD` | Secret | as above | Key password |
| `PLAY_SERVICE_ACCOUNT_JSON` | Secret | all six, including `production` | Play Developer API service-account JSON key (one account for all apps — deviation DA-1, ADR 0022) |
| `UPLOAD_CERT_SHA256` | Variable | `dev`, `test`, `perf-test`, `uat`, `prep-prod` | SHA-256 of that environment's upload certificate |
| `CR_APP_CFG_API_BASE_URL` | Variable | the same five | API gateway base URL (https) for that stage; reference data is `<base>reference-data-service/` |
| `CR_APP_CFG_USE_STUB_AUTH` | Variable | the same five | `true` / `false` |
| `CR_APP_CFG_USE_FAKE_SIGN_IN` | Variable | the same five | `false` for real Entra sign-in (required) |
| `CR_APP_CFG_MSAL_CLIENT_ID`, `CR_APP_CFG_MSAL_AUTHORITY_URL` | Variable | the same five | That stage's Entra External ID app registration and tenant authority (ADR 0019) |
| `CR_APP_CFG_MSAL_REDIRECT_URI` | Variable | optional | `msauth://<that app's package>/<app-signing SHA-1, base64, URL-encoded>` for the browser fallback |
| `CR_APP_CFG_MSAL_API_SCOPE` | Variable | the same five | Entra API scope requested at sign-in and for API tokens (e.g. `api://<app-id>/access_as_user`); real sign-in fails fast if unset |

`production` needs only `PLAY_SERVICE_ACCOUNT_JSON`. Configuration values are variables, never secrets:
anything in the app package can be read. To **add a config value**, set `CR_APP_CFG_<KEY>` on each
Environment first, then in one PR declare the key in the schema, read it in the app and bump the version.

### Production runbook (prep-prod → sanity → promote → timed Publish)

1. Before the release, update `fastlane/release-notes/production/en-GB.txt` (and `cy.txt` if used) in the PR
   that produces the release tag. The `production` job reads the notes at the tag, so they cannot change
   after tagging; with `distribution=manual` they are typed in Play Console instead.
2. Run **Android Release** on the tag (`prod` on). `prep-prod` uploads to the closed `prep-prod` track, and
   the `production` job then waits for its reviewers. Do not approve it yet.
3. Wait for Google's review to reach *Ready*, then the release manager clicks **Publish changes** in Play
   Console (Prod app). The API cannot do this.
4. Sanity testers install the build from `prep-prod` and sign it off.
5. Check the release notes, then approve the `production` job. It promotes the same versionCode to
   `production`; if another change in the Prod app is still in review it fails without restarting that
   review — re-run it once the review completes. A pending approval expires after 30 days, so re-run the
   workflow if sanity takes longer.
6. After review, click **Publish changes** at the agreed go-live time. There is no GitHub job for go-live.

With `distribution=manual`, do steps 2 and 5 by hand using the checklist each job prints (upload the AAB to
the named app and track; promote the `prep-prod` release to Production).

### One-off setup (manual)

These cannot be done by the pipeline and are pending:

1. Create a **non-prod upload key** for the Dev and Test apps; keep the Prod upload key out of `dev`
   (remove it if present). Never commit keystores.
2. Create the **Dev** and **Test** Play apps and do the **first upload of each by hand** (the API cannot make
   it): run **Android Release** with `distribution=manual`, download the AAB for `dev` / `test`, upload it
   to the app's internal track in Play Console, and choose Play App Signing.
3. Create the closed tracks **`perf-test`** (Test app) and **`prep-prod`** (Prod app) with exactly those
   names. Turn **Managed Publishing on in the Prod app only**.
4. Create the six GitHub Environments with the secrets and variables above.
5. Invite the Play **service account** to all three apps (release to testing and production tracks) before
   using `distribution=play`.
6. Set the tester lists so each person is on **one track per app**, and tell testers that switching a device
   between tracks with different backends (`uat` ↔ `prep-prod`/`production`, `test` ↔ `perf-test`) means
   **uninstalling the app first, which removes its data** (ADR 0022, *Switching tracks on one device*).
7. Add a **tag ruleset** for `v*`: only GitHub Actions may create release tags, and nobody may move or
   delete them, so "the code at the tag" can be trusted.

SonarCloud analysis is configured in `sonar-project.properties` and the scan step runs in the workflow, but
it cannot pass until the `DEFRA_mmo-cr-android` SonarCloud project and the `MMO_CR_SONAR_TOKEN` secret
exist.

### Job ordering

`instrumented-tests` declares `needs: validate`, so it only starts once lint, build and unit tests pass.
GitHub Actions dependencies are job-level — a job cannot be made to start after an individual *step* of
another job — so gating on the whole `validate` job is the closest expressible equivalent. This trades a
little wall-clock time for not spending ~20 minutes of emulator runner on code that does not compile.

### Emulator: why a third-party action

`reactivecircus/android-emulator-runner` is used because **no official Google or GitHub action exists for
running Android emulators**. It is not a replacement for the official tooling — it is a wrapper around it,
which per run:

1. installs `platform-tools`, `platform`, `emulator` and `system-images` via `sdkmanager`
2. creates the AVD via `avdmanager`
3. boots `emulator`, then polls until `sys.boot_completed` is set
4. runs the supplied script, then tears the emulator down

Steps 3 and 4 (boot detection, timeouts, reliable teardown) are the parts that are genuinely awkward to
hand-roll in shell, and are the main reason for using it. It is the de-facto standard on Android CI and is
used by Google's own repositories (`android/compose-samples`, `google/accompanist`, `google/android-fhir`).

**KVM is required.** The x86_64 emulator depends on VM acceleration; without it the emulator falls back to
software emulation and is far too slow for CI. `/dev/kvm` is not accessible to the runner user by default,
so the workflow installs a `udev` rule granting access before the emulator starts. This is the approach
documented by the action itself.

**Target configuration:** the emulator runs API 36 with the headless-optimised `aosp_atd` system image on
a `pixel_6` device profile, matching the app's `targetSdk 36`.

**Alternative under consideration:** [Gradle Managed Devices](https://developer.android.com/studio/test/gradle-managed-devices)
would move device definitions into `app/build.gradle.kts` and let AGP provision and tear down emulators,
removing the third-party action entirely. Not yet adopted.

## Governance notes / DEFRA standard deviations

The following are recorded exceptions/deviations that must be logged with Delivery Architecture
(delivery.architecture@defra.gov.uk):

- Building a **native Android app** rather than following the DEFRA mobile standard's default guidance
  (ADR 0001).
- Using **Koin** instead of the copilot-instructions §5 proposed default of Hilt for DI (ADR 0004).
- Using **Mockito** instead of MockK for unit testing (team preference/expertise).
- Not bundling **GDS Transport** font (not licensed for non-gov.uk products); using the system-font
  fallback behind a single swap-point.
