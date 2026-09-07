# 0004 - Koin for dependency injection

## Status

Superseded by [0005 - Hilt for dependency injection](0005-hilt-dependency-injection.md). This decision was
reverted before public beta; the app now uses Hilt (the copilot-instructions §5 default) and no longer
carries this deviation.

## Context

copilot-instructions.md §5 proposes **Hilt** as the default DI framework for Android apps in this
environment (Google/community convention, annotation-processor based, tightly integrated with the Android
framework lifecycle). The team evaluated Hilt against Koin for this project.

## Decision

Use **Koin** (`koin-android`, `koin-androidx-compose`, `koin-bom`) instead of Hilt.

Rationale:
- Koin's DSL-based modules avoid annotation-processing build overhead (no extra KSP/kapt round trip for
  DI, on top of the Room KSP processor already in the build).
- The team has existing Koin expertise, reducing onboarding time and review friction.
- Koin integrates cleanly with `koinViewModel()` in Compose for the `SessionCoordinator` and future
  ViewModels used in this app.

## Consequences

- **This is a recorded deviation from the copilot-instructions §5 default (Hilt).** It must be flagged and
  logged as a governance exception with Delivery Architecture (delivery.architecture@defra.gov.uk).
- Koin resolves dependencies at runtime rather than compile time, so a missing binding surfaces as a
  runtime crash rather than a compile error; this is mitigated by keeping DI modules small, reviewed, and
  covered by a smoke/instrumented test that starts the app's Koin graph.
- Consistent with the "DEFRA > GDS > Google/Android > community" precedence: no DEFRA or GDS standard
  mandates a specific Android DI framework, so this project-level choice is permissible provided it is
  disclosed.
