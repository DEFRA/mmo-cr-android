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

# Build
./gradlew assembleDebug

# Run unit tests
./gradlew testDebugUnitTest

# Run unit tests + generate coverage report (Kover)
./gradlew testDebugUnitTest koverXmlReportDebug

# Lint / static analysis
./gradlew ktlintCheck detekt lintDebug --continue

# Install & run on a connected device/emulator
./gradlew installDebug
```

The same steps are also exposed as Fastlane lanes, which is exactly what CI runs:

```powershell
bundle install
bundle exec fastlane lint              # ktlintCheck + detekt + lintDebug (--continue)
bundle exec fastlane build             # assembleDebug
bundle exec fastlane test              # testDebugUnitTest + koverXmlReportDebug
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
| [0000](docs/adr/0000-ios-adr-references-TBC.md) | iOS ADR references — TBC |

## CI

`.github/workflows/android-ci.yml` runs lightweight **PR validation only** (static analysis, debug
assemble, unit tests with coverage, and emulator-based instrumented/accessibility tests). Every Gradle
invocation goes through a Fastlane lane in `fastlane/Fastfile`, so CI and local runs are identical.

It does **not** perform release signing or Play Store publishing — that is a separate
release-engineering/DevOps responsibility, out of scope for this workflow.

SonarCloud analysis is configured in `sonar-project.properties` but the scan step in the workflow is
**commented out** until the `DEFRA_mmo-cr-android` SonarCloud project and the `MMO_CR_SONAR_TOKEN` secret
exist. Actions are currently pinned by version tag rather than commit SHA for readability; they must be
re-hardened to SHAs (a DEFRA supply-chain requirement) before this workflow gates production releases.

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

**Target configuration:** the emulator runs API 35 with the headless-optimised `aosp_atd` system image on
a `pixel_6` device profile, matching the app's `targetSdk 35`.

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
