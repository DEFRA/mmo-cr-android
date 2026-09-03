---
description: "Kotlin and Jetpack Compose coding standards for the MMO Catch Recording Android app: Kotlin/Android style guides, Compose-first patterns, MVVM + unidirectional data flow, Coroutines/Flow, project layout, offline-first data. Use when writing or reviewing Kotlin/Compose code."
applyTo: "**/*.kt"
---

# Kotlin & Jetpack Compose standards

Precedence: DEFRA standards > GDS > Google/Android guidelines. Where DEFRA is silent, follow the
[Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html), the
[Android Kotlin style guide](https://developer.android.com/kotlin/style-guide) and the
[Compose API guidelines](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/docs/compose-api-guidelines.md).

## Language & style

- **Naming:** Clarity at the point of use. `UpperCamelCase` for types, `lowerCamelCase` for members.
  Composable functions are `UpperCamelCase` nouns (`CatchRow`). Omit needless words; name by role, not
  type. Booleans read as assertions (`isValid`, `hasCatch`).
- **Immutability & null-safety:** Prefer `val` and immutable data. Use nullable types meaningfully; avoid
  `!!` and unsafe casts (`as`) outside tests. Handle `null` and errors explicitly; prefer `?.`/`?:`/
  `sealed`/`Result`.
- **Value types & data:** Use `data class`/`enum class`/`sealed`/value classes for domain and UI state.
  Prefer pure functions; keep side effects explicit.
- **Errors:** Model errors as `sealed` hierarchies or typed exceptions; never swallow errors silently (see
  logging).
- **Docs:** Write a KDoc `/** … */` summary for public/non-obvious declarations.
- **Formatting:** Use ktlint/detekt if configured; do not fight the formatter.
- **Minimum SDK:** API 26 (Android 8.0). Guard newer APIs with `Build.VERSION.SDK_INT` checks or
  `@RequiresApi`.

## Compose architecture

- **Compose-first, Material 3.** Drop to Views/`AndroidView` only when Compose cannot do the job; isolate it.
- **Small, composable functions.** Extract child composables; a screen should read at a glance. No business
  logic in composables.
- **Unidirectional data flow.** Composables render immutable UI state and emit events upward. Keep logic in
  view models/use-cases. **Hoist state**; prefer stateless composables.
- **State tools:** `remember`/`rememberSaveable` for local UI state, `State`/`StateFlow` collected with
  `collectAsStateWithLifecycle()` for view-model state, and slot APIs for composition. Avoid god
  view-models — split by responsibility.
- **Navigation:** Use Navigation-Compose with a typed, testable route model.

## Architecture pattern

- If the repo already establishes a pattern, **follow it**. If none exists, **ask the developer** which
  to adopt (default recommendation: **MVVM + unidirectional data flow**, with **Hilt** DI and a
  use-case/repository layer). Record the decision as an ADR under `docs/adr/`.
- Layer responsibilities: **UI (Compose)** → **ViewModel (UI state + events)** → **UseCase/Repository**
  (domain + IO) → **Networking/Persistence**. Depend on interfaces; inject dependencies for testability.

## Concurrency

- Use **Kotlin Coroutines + Flow** with structured concurrency. Avoid nested callbacks.
- Launch from `viewModelScope`/lifecycle scopes; do IO/CPU work on the appropriate `Dispatchers`
  (injected, not hard-coded). Expose cold `Flow`s / `StateFlow`; collect with lifecycle awareness.
- Make shared mutable state safe (confine to a dispatcher, use `Mutex`/immutable snapshots). Avoid leaking
  coroutines; cancel with scope.

## Offline-first data (mandatory)

- Treat the network as unavailable by default. Every feature must degrade gracefully offline and sync
  when back online.
- Persist locally (**Room** as the offline source of truth); reconcile with the backend REST API
  deliberately (define conflict resolution). Use **WorkManager** for the offline mutation/sync queue that
  flushes on reconnect. Use **DataStore** for preferences.
- Represent load/empty/error/offline states explicitly in the UI. Never show a spinner forever.
- Networking layer: typed requests/responses (Retrofit + kotlinx.serialization/Moshi), retry/backoff and
  timeouts (OkHttp), and an offline queue for mutations (create/update catch records) that flush on
  reconnect.

## Dependencies

- **Gradle (Kotlin DSL) with a version catalog** (`gradle/libs.versions.toml`). No other build/dependency
  system. Vet packages per DEFRA
  [choosing packages](https://defra.github.io/software-development-standards/guides/choosing_packages/) —
  prefer well-maintained, licence-compatible, minimal dependencies. Pin versions.

## Suggested project layout

```
mmo-cr-android/
├── settings.gradle.kts / build.gradle.kts   # Gradle Kotlin DSL, version catalog
├── gradle/libs.versions.toml
├── app/
│   └── src/main/java/mmo/catchrecording/android/
│       ├── MMOCatchRecordingApp.kt          # Application (Hilt @HiltAndroidApp)
│       ├── di/                              # Hilt modules (composition root)
│       ├── feature/                         # one package per feature (screens + view models + models)
│       ├── core/
│       │   ├── network/                     # Retrofit API, DTOs, error mapping
│       │   ├── persistence/                 # Room, SyncWorker, offline mutation queue, migrations
│       │   ├── model/                       # domain models
│       │   └── designsystem/                # reusable accessible components, theme, typography
│       └── support/                         # logging, extensions, utilities
├── app/src/test/                            # JVM unit tests mirror the source tree
├── app/src/androidTest/                     # Compose UI + Espresso + accessibility tests
```
