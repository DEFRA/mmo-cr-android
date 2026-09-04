# 0002 - MVVM + Clean Architecture

## Status

Accepted

## Context

The app needs a maintainable, testable structure that keeps UI (Compose), state management and business
rules separate, supports offline-first sync logic, and scales as new catch-recording features are added.
Google's official Android architecture guidance recommends a UDF (unidirectional data flow) UI layer over
a domain/data layer.

## Decision

Adopt **MVVM + Clean Architecture**, layered as:

- **presentation** — Compose UI (stateless composables) + ViewModels (`BaseViewModel`, `StateFlow<UiState>`,
  UDF `dispatch(event)` surface).
- **domain** — use-cases and pure business rules (e.g. validation), no Android framework dependencies.
- **data** — repositories, Room persistence, network/sync logic, implementing domain-defined interfaces.

ViewModels depend on domain interfaces, never directly on data-layer implementations, so tests can inject
fakes/mocks. Compose UI holds no business logic; state is hoisted to ViewModels.

## Consequences

- Each layer is independently unit-testable (ViewModels with fakes/Mockito, domain logic with pure JUnit).
- Slightly more boilerplate (interfaces + DI bindings) than a flatter structure, considered acceptable for
  the long-lived, evolving nature of this app.
- Aligns with Google's official Android architecture guidance (community/Google precedence tier).
