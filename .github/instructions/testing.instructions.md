---
description: "Testing standards for the MMO Catch Recording Android app: unit tests (JUnit/MockK/Turbine), Compose UI & Espresso instrumented tests, accessibility tests, offline/sync tests, coverage (Kover) and SonarCloud. Use when writing or reviewing tests or setting quality gates."
applyTo: "**/src/test/**/*.kt, **/src/androidTest/**/*.kt, **/*Test.kt, **/*Tests.kt"
---

# Testing standards

Follow DEFRA [quality assurance and test standards](https://defra.github.io/software-development-standards/standards/quality_assurance_standards/).
New or changed behaviour ships with tests. Track coverage in
[DEFRA SonarCloud](https://sonarcloud.io/organizations/defra) and keep the quality gate green.

## What to test

- **Unit tests** for view models, use-cases, repositories, networking mappers, sync/offline logic and
  domain rules. Prefer fast, isolated, deterministic tests. Inject dependencies via interfaces and use
  test doubles/fakes/mocks — no real network in unit tests.
- **UI tests** for critical user journeys (e.g. record a catch offline → sync when online), using
  **Compose UI test** (`createAndroidComposeRule`) and/or **Espresso**. Drive elements by stable
  **`testTag`s** / semantics, not by display text alone.
- **Accessibility tests** — assert `contentDescription`/roles/semantics exist; enable `AccessibilityChecks`
  in Espresso and use the Accessibility Test Framework (see
  [android-accessibility-audit skill](../skills/android-accessibility-audit/SKILL.md)).
- **Offline/sync tests** — simulate no-connectivity, queued mutations (WorkManager `TestListenableWorkerBuilder`),
  reconnect and conflict resolution.

## Frameworks

- **JUnit** is the baseline; **MockK** for mocking, **Turbine** for `Flow`, **kotlinx-coroutines-test**
  (`runTest`) for coroutines, **Robolectric** for JVM-side Android unit tests, **Compose UI test** /
  **Espresso** for instrumented tests. Keep a consistent choice per module.
- Structure tests **Arrange → Act → Assert** (Given/When/Then). One behaviour per test; descriptive names
  (`saveCatch_whenOffline_queuesForSync`) — backtick-named test functions are encouraged.

## Coverage targets

Coverage must be **visible and reported** in [DEFRA SonarCloud](https://sonarcloud.io/organizations/defra)
and must not regress below the established baseline. The project quality gate is:

- **≥90%** overall (global) line/branch coverage.
- **≥95%** for core business logic — view models, use-cases, repositories, sync/offline logic and domain
  rules.
- **100%** for error-handling and security-critical paths — auth, token/Keystore handling, input
  validation and conflict resolution.

Write tests alongside the code (same change, not a follow-up), and run the full suite after every change —
`./gradlew testDebugUnitTest connectedDebugAndroidTest` / `fastlane test` — confirming all tests pass
before moving on. Report coverage with **Kover** (Compose-friendly) and upload to SonarCloud.

## Conventions

- Mirror the source tree: `src/test/` for JVM unit tests, `src/androidTest/` for instrumented UI/accessibility.
- Make tests independent and order-agnostic; no shared mutable global state; reset state in `@Before`.
- Prefer testing behaviour/outcomes over implementation details. Avoid flaky timing — use `runTest`,
  idling resources and controllable clocks, not `Thread.sleep`.
- Keep fixtures/sample payloads in `resources`; do not hit live services.

## Running

- Local/CI: `./gradlew testDebugUnitTest` (unit) and `./gradlew connectedDebugAndroidTest` (instrumented,
  on an emulator/Gradle Managed Device) — or `fastlane test`. Every PR runs build + test + lint +
  SonarCloud before merge.
- Device coverage: also validate on a **representative range of real devices** and current + previous
  Android versions per DEFRA mobile standards; enrol a device in the Android beta programme to catch
  upcoming issues.
