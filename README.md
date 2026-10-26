# MMO Catch Recording (Android)

Native Android app for the Marine Management Organisation (MMO) Catch Recording service, built for DEFRA.
See [`docs/adr/0001-native-android-app-exception.md`](docs/adr/0001-native-android-app-exception.md) for
why this is a native app (a governed exception to the DEFRA mobile standard's default guidance).

## Quick start

Prerequisites: JDK 21, Android Studio (latest stable), an Android device/emulator running API 26+.
Fastlane (optional locally, used by CI) additionally needs Ruby 3.3+ and Bundler.

```powershell
git clone <this-repo-url>
cd mmo-cr-android

# Point debug builds at a backend (git-ignored; see docs/development/reference-data.md)
#   local.properties: crAppCfg.API_BASE_URL=http://10.0.2.2:3002/  and  crAppCfg.USE_STUB_AUTH=true

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
0017](docs/adr/0017-packaged-app-configuration.md)) — see
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
- **Security** — `androidx.biometric` + `androidx.security:security-crypto` are wired as dependencies;
  Stage 1 ships interfaces and in-memory fakes only. Real implementations must use Android Keystore
  (+ Tink) for data-at-rest, never plain `EncryptedSharedPreferences`, and must never log biometric/session
  state or PII — see the KDoc on each interface in `core/security/`.
- **Offline fisheries statistical sub-rectangle map** — a fully offline, custom Compose `Canvas`-rendered
  map (no map SDK/tiles/API key/network) with a build-time GeoJSON preprocessing task in the `build-logic/`
  composite Gradle build — see ADR 0013 and [`docs/development/offline-map.md`](docs/development/offline-map.md).
- **Reference-data API integration** — Retrofit/OkHttp-backed, cache-first (Room) with a bundled offline
  fallback; the backend comes from the packaged app config — see ADR 0015/0016/0017 and
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
| [0008](docs/adr/0008-reference-data-stub.md) | Reference-data stub (superseded by 0016) |
| [0013](docs/adr/0013-offline-fisheries-map-rendering.md) | Offline fisheries statistical sub-rectangle map rendering |
| [0014](docs/adr/0014-catch-record-status-audit-and-offline-sync-confirmation.md) | Catch-record status, audit timestamps and offline-sync confirmation |
| [0015](docs/adr/0015-http-client-retrofit-okhttp.md) | HTTP client: Retrofit + OkHttp |
| [0016](docs/adr/0016-reference-data-api-integration.md) | Reference-data API integration |
| [0017](docs/adr/0017-packaged-app-configuration.md) | Packaged app configuration (replaces build flavors) |
| [0018](docs/adr/0018-android-release-poc.md) | Android release pipeline — POC (version in code, tags, signing, Play tracks) |
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

## Release (POC — ADR 0018)

**Version.** [`version.properties`](version.properties) is the single source of `VERSION_NAME` and
`VERSION_CODE`. Every non-Dependabot PR must raise `VERSION_CODE` (it never resets) and must not lower
`VERSION_NAME`. When CI passes on `main`, it tags the commit `v<VERSION_NAME>-BUILD_<VERSION_CODE>`, e.g.
`v1.0.1-BUILD_3`. **POC only:** a manual *Android CI* run (Run workflow → pick the branch → tick
`publish_release_tag`) tags any branch; this reverts to `main`-only after the POC (ADR 0018).

**Run a release.** Actions → **Android Release** → Run workflow → *Use workflow from* → **Tags** → the
release tag, then pick a `target`:

| Target | What happens |
|---|---|
| `build-only` | Signed AAB kept as a workflow artifact for 1 day (used once, for the manual first upload) |
| `internal` | Uploaded to the Play **internal testing** track — *parked: needs `PLAY_SERVICE_ACCOUNT_JSON` (ADR 0018)* |
| `dev` | Uploaded to the Play closed testing track **`dev`** — *parked: needs `PLAY_SERVICE_ACCOUNT_JSON` (ADR 0018)* |

The run refuses a branch, or a tag that doesn't match `version.properties` at that commit. Each upload
needs an unused `VERSION_CODE`: to send the same code to another track, bump and release the new tag.

**What the `release` lane does** ([`fastlane/lanes/release.rb`](fastlane/lanes/release.rb)): validates
the `dev` Environment's `CR_APP_CFG_*` variables against
[`config/app-config.schema.json`](config/app-config.schema.json) before compiling → stops if the
versionCode is already on a track → builds the unsigned release AAB with that config packaged → signs it
with the upload key (`jarsigner`; Play App Signing holds the app signing key) → verifies the signature and
the packaged config → uploads (or keeps the AAB for `build-only`).

**GitHub Environment `dev`** (deployments only from `v*` tags):

| Name | Kind | Value |
|---|---|---|
| `CR_APP_CFG_API_BASE_URL` | Variable | Reference-data service root (https) |
| `CR_APP_CFG_USE_STUB_AUTH` | Variable | `true` / `false` |
| `UPLOAD_KEYSTORE_BASE64` | Secret | Upload keystore (PKCS12), base64 on one line |
| `UPLOAD_KEYSTORE_PASSWORD` | Secret | Keystore password |
| `UPLOAD_KEY_ALIAS` | Secret | Key alias |
| `UPLOAD_KEY_PASSWORD` | Secret | Key password (same as the keystore's for PKCS12) |
| `PLAY_SERVICE_ACCOUNT_JSON` | Secret | Play Developer API service-account JSON key — not set yet; until then upload `build-only` artifacts by hand |

**Add a config value.** Set `CR_APP_CFG_<KEY>` on the Environment first, then in one PR declare the key in
the schema, read it in the app and bump the version. Values are never secrets: anything in the app
package can be read.

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
