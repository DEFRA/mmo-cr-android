# 0012 - App-wide language preference persisted via DataStore

## Status

Accepted

## Context

`AppLanguageProvider` (`common/design/AppLanguage.kt`) wraps a screen's content in a locale-overridden
`Context` so `stringResource` resolves English/Welsh without an Activity restart. Every screen that used it
(`HomeScreen`, `SignInScreen`, `CatchRecordWizardScaffold`) held its **own** `rememberSaveable { mutableStateOf("en") }`
— language was neither shared across screens (switching language on one screen did not affect any other) nor
persisted (an app restart, or even some configuration/process-death recreations, silently reset it back to
English), which does not meet the Welsh Language requirement expected of a GOV.UK/DEFRA service.

`AppLanguageProvider` additionally called `Locale.setDefault(locale)` before building the configuration
context. Mutating the JVM-wide default `Locale` from a `@Composable` is an unsafe, process-global side
effect: it silently changes locale-sensitive behaviour for *every* other component in the process (including
unrelated formatting elsewhere, e.g. `String.format`, number/date parsing not routed through the wrapped
`Context`), is invoked on every recomposition of every wrapped screen, and is exactly the kind of static
mutable global state the Kotlin/Compose instructions prohibit for anything beyond a screen-local presentation
concern.

## Decision

- Introduce **`AppLanguageRepository`** (`core/language`), backed by **Jetpack DataStore
  (`androidx.datastore:datastore-preferences`)**, exposing `val language: Flow<String>` (defaulting to
  `"en"`) and `suspend fun setLanguage(language: String)`. This is the single source of truth for the user's
  language preference, persisted to disk and surviving process death and app restarts.
- Add a small `@HiltViewModel` **`AppLanguageViewModel`** exposing the repository's flow as a
  `StateFlow<String>` plus a `toggleLanguage()`/`setLanguage(String)` action, used by the three real (not
  preview-only) call sites — `CatchRecordWizardScaffold`, `HomeScreen`, `SignInScreen` — in place of their
  former per-screen `rememberSaveable` language state. Each call site obtains its own `hiltViewModel()`
  instance (destination-scoped, per existing Hilt Navigation Compose usage elsewhere in the app), but all
  instances read/write the same underlying DataStore-persisted value, so the effective language is
  consistent across the whole app and across navigation/process recreation, without requiring a single
  shared ViewModel instance to be threaded through the composition tree.
- **`AppLanguageProvider` no longer calls `Locale.setDefault`.** It only builds a locale-overridden
  `Context` (via `Context.createConfigurationContext`) and provides that through `LocalContext` — exactly the
  documented, side-effect-free pattern for scoping a locale override to a Compose subtree. The process-wide
  JVM default locale is left untouched.

## Consequences

- `androidx.datastore:datastore-preferences` is a new, small, additive dependency (pinned in the Gradle
  version catalog) — the standard, non-deprecated Jetpack replacement for `SharedPreferences`-based
  persistence, consistent with the DEFRA/Android defaults already used elsewhere in the app (Room, DataStore
  is Room's sibling library from the same team) and unrelated to the `androidx.security-crypto` rejection in
  ADR 0006 (this preference is not sensitive/security data).
- Because `Locale.setDefault` is no longer called, any code that still relies on the process-wide default
  locale (rather than the composition-local `Context`) for locale-sensitive formatting will **not** pick up
  the in-app language toggle. No such code was identified in this app at the time of writing; new code must
  read locale-sensitive data through the composition's `LocalContext`/`LocalConfiguration`, not
  `Locale.getDefault()`, to stay correct under this model.
- The language preference is intentionally excluded from neither backup path (unlike the SQLCipher draft
  database/passphrase — see ADR 0006's backup exclusions) since it carries no sensitive data and restoring a
  user's language preference on a new/restored device is desirable.
