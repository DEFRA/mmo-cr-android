# 0005 - Hilt for dependency injection

## Status

Accepted

## Context

[ADR 0004](0004-koin-dependency-injection.md) adopted Koin as a recorded deviation from the
copilot-instructions §5 proposed default (Hilt). The team has since decided to revert that deviation and
align with the copilot-instructions §5 default before public beta.

## Decision

Use **Hilt** (`hilt-android`, `hilt-android-compiler`, `androidx.hilt:hilt-navigation-compose`) for
dependency injection, replacing Koin.

Rationale:
- Aligns with the copilot-instructions §5 default, removing a recorded governance deviation.
- Compile-time-verified dependency graph: a missing binding is a build error, not a runtime crash — a
  stronger safety property than Koin's runtime resolution, and preferable for a DEFRA production app.
- `hilt-android-compiler` runs via **KSP** (supported since Dagger/Hilt 2.51+), consistent with the Room
  KSP processor already in the build — no separate kapt round trip is introduced.
- `androidx.hilt:hilt-navigation-compose` provides `hiltViewModel()`, a drop-in replacement for Koin's
  `koinViewModel()` in Compose.

## Consequences

- `MmoApplication` is annotated `@HiltAndroidApp`; `MainActivity` is annotated `@AndroidEntryPoint`.
- All ViewModels (`SessionCoordinator`, `SignInViewModel`, `HomeViewModel`, `CatchRecordViewModel`,
  `MapViewModel`) are annotated `@HiltViewModel` with an `@Inject`-annotated constructor.
- Stage-1 fakes and simple value providers (dispatcher, clock, id factory, reentry policy) are bound via
  `@Provides` methods in `di/AppModule.kt`, a single `@Module @InstallIn(SingletonComponent::class) object`
  — the direct Hilt equivalent of the former Koin module. Real Keystore/DataStore/Room/Retrofit-backed
  implementations replace these fakes in later stages without changing consumer code.
- No DEFRA or GDS standard mandates a specific Android DI framework, so this project-level choice remains
  permissible; it is now the un-deviated copilot-instructions §5 default and requires no governance
  exception.
