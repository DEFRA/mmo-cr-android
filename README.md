# MMO Catch Recording (Android)

Native Android app for the Marine Management Organisation (MMO) Catch Recording service, built for DEFRA.
See [`docs/adr/0001-native-android-app-exception.md`](docs/adr/0001-native-android-app-exception.md) for
why this is a native app (a governed exception to the DEFRA mobile standard's default guidance).

## Quick start

Prerequisites: JDK 17, Android Studio (latest stable), an Android device/emulator running API 26+.

```powershell
git clone <this-repo-url>
cd mmo-cr-android

# Build
./gradlew assembleDebug

# Run unit tests
./gradlew testDebugUnitTest

# Run unit tests + generate coverage report (Kover)
./gradlew testDebugUnitTest koverXmlReport

# Lint / static analysis
./gradlew ktlintCheck detekt lint

# Install & run on a connected device/emulator
./gradlew installDebug
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
├── di/                 # Koin modules
└── MmoApplication.kt   # Application class, starts Koin
```

Feature work (once past this foundational stage) should follow the same `domain` / `data` / `presentation`
split per feature package, per
[`docs/adr/0002-mvvm-clean-architecture.md`](docs/adr/0002-mvvm-clean-architecture.md).

## Architecture & key decisions

- **MVVM + Clean Architecture** — see ADR 0002.
- **Room** for offline-first local persistence (Stage 1: compile-time stub only) — see ADR 0003.
- **Koin** for dependency injection (a recorded deviation from the copilot-instructions §5 Hilt default)
  — see ADR 0004.
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
| [0004](docs/adr/0004-koin-dependency-injection.md) | Koin for dependency injection |
| [0000](docs/adr/0000-ios-adr-references-TBC.md) | iOS ADR references — TBC |

## CI

`.github/workflows/android-ci.yml` runs lightweight **PR validation only** (lint, unit tests, debug
assemble, coverage report). It does **not** perform release signing or Play Store publishing — that is a
separate release-engineering/DevOps responsibility, out of scope for this workflow.

## Governance notes / DEFRA standard deviations

The following are recorded exceptions/deviations that must be logged with Delivery Architecture
(delivery.architecture@defra.gov.uk):

- Building a **native Android app** rather than following the DEFRA mobile standard's default guidance
  (ADR 0001).
- Using **Koin** instead of the copilot-instructions §5 proposed default of Hilt for DI (ADR 0004).
- Using **Mockito** instead of MockK for unit testing (team preference/expertise).
- Not bundling **GDS Transport** font (not licensed for non-gov.uk products); using the system-font
  fallback behind a single swap-point.
