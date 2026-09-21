# 0011 - Structured logging with Timber and a safe user-facing error mapper

## Status

Accepted

## Context

A remediation review found `CatchRecordFlowViewModel` (and other ViewModels) surfaced raw exception messages
directly to the UI, e.g. `UiStatus.Error(message = error.message ?: "...", isRetryable = true)`. Per the
security instructions ("no PII/secrets in logs or error messages") and the error-handling standards ("never
surface `error.message`"), an underlying exception's message can originate from a lower layer (SQLite,
Room, a future real network client) and may embed data (identifiers, raw payload fragments, file paths) that
is not safe to show a user or write to `Log`/`Logcat` verbatim, and is never localizable (English-only,
un-reviewed copy).

There was also no structured logging library in the app at all — diagnostics relied entirely on default
`Log`/stack traces, with no consistent redaction discipline or a release-vs-debug verbosity split.

## Decision

- Adopt **Timber** for all first-party structured logging. `MmoApplication.onCreate()` plants
  `Timber.DebugTree()` only when `BuildConfig.DEBUG`; release builds plant a minimal custom
  `ReleaseTree` that drops `VERBOSE`/`DEBUG` priority entirely and, for `WARN`/`ERROR`, logs the tag and a
  caller-supplied message only — it never appends a `Throwable`'s `.message`/stack trace to the log output
  (the exception's *type* may be logged for triage; its message is presumed unsafe by default). This keeps a
  configurable debug-level path for local development while guaranteeing release builds cannot leak PII/
  secrets through an exception message a lower layer generated.
- Add a small, pure, unit-testable **`SafeErrorMapper`** (`core/error`) that classifies a `Throwable` into
  a `CatchRecordErrorClassification` (`Transient` — offline/IO, retryable; `Validation` — a caught domain
  validation failure, terminal but actionable; `Terminal` — anything else, e.g. a corrupted-data mapping
  failure) and returns an already-safe, generic, plain-English message plus the correct `isRetryable` flag.
  It never reads or forwards `Throwable.message`; call sites additionally log the throwable via Timber
  (`Timber.w(throwable, "tag")`) for diagnostics, redacted per the `ReleaseTree` policy above.
- `CatchRecordFlowViewModel.emitLoadError` (and the equivalent in `CatchRecordSyncWorker`) now goes through
  `SafeErrorMapper` instead of reading `error.message` directly.

## Consequences

- Every ViewModel error path must route through `SafeErrorMapper` (or an equivalent typed mapper) rather than
  constructing `UiStatus.Error` from a raw exception directly — flagged in code review as a regression if a
  future change reintroduces `error.message`.
- The mapped user-facing strings are currently plain hardcoded English constants (consistent with this
  ViewModel layer's pre-existing pattern of non-resource-based fallback strings), not yet resolved through
  `stringResource`/Welsh translation — Compose-layer copy (button labels, screen titles, static in-screen text)
  continues to use `R.string` resources as normal and is fully bilingual. Localizing the dynamic ViewModel
  error strings themselves would require threading a string-resource provider into every ViewModel and is
  tracked as a follow-up, not part of this remediation's scope.
- `Timber` is a new, small, additive dependency (pinned in the Gradle version catalog); it has no transitive
  Android-version or licensing implications relevant to the DEFRA/GDS constraints already recorded in
  copilot-instructions §5.
