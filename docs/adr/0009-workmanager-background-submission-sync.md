# 0009 - WorkManager for background catch-record submission sync

## Status

Accepted

## Context

Phase 8 (check-your-answers + submission) must support the offline-first requirement that a catch record
can be submitted even with no connectivity at the moment the user taps "Accept and submit trip details".
Per GDS/DEFRA offline-first conventions, a transient/no-connectivity condition is never terminal — the
record is saved locally and sent automatically once connectivity returns, without the user having to
remember to retry manually.

This introduces a genuinely new architectural surface not present anywhere else in the app yet: a
**background job that must survive process death and app restarts, wait for a connectivity constraint, and
retry with backoff** until it succeeds. No prior background-scheduling mechanism (`WorkManager`,
`AlarmManager`, a foreground service, etc.) exists in the codebase — this is the first. Given its scope
(new dependency, new Hilt integration point, new manifest configuration, a new failure/retry contract that
other developers will need to understand and extend for future sync needs, e.g. Phase 6/7 landing-storage
data), this warrants its own ADR rather than being treated as an incidental implementation detail of Phase 8.

## Decision

Use **`androidx.work` (WorkManager) with Hilt-Work (`androidx.hilt:hilt-work` + `HiltWorkerFactory`)** to
schedule and execute the background submission retry:

- `CatchRecordSubmissionRepository.submit(draft)` is the single submission entry point, called identically
  by both the online path (`CatchRecordFlowViewModel.acceptDeclarationAndSubmit`, invoked directly) and the
  offline/retry path (`CatchRecordSyncWorker`, invoked by WorkManager) — there is exactly one submission
  code path, never two divergent implementations.
- `CatchRecordSyncScheduler` (domain interface) / `WorkManagerCatchRecordSyncScheduler` (impl) enqueues a
  single `OneTimeWorkRequest` for `CatchRecordSyncWorker`, constrained to `NetworkType.CONNECTED`, whenever
  a draft is left in `DraftStatus.PendingSync` — either because the device was offline at submit time, or
  because an online submission attempt failed transiently.
- The work request is enqueued with `enqueueUniqueWork(..., ExistingWorkPolicy.REPLACE, ...)` keyed by
  `draftId`, so re-entering the flow for the same still-pending draft never piles up duplicate queued work.
- `CatchRecordSyncWorker` is a `@HiltWorker` `CoroutineWorker`: it re-fetches the draft, no-ops
  (`Result.success()`) if it's no longer `PendingSync` (already handled by an earlier run of the same work,
  e.g. after process death), otherwise calls `submit(draft)` and marks the draft `Submitted` on success.
- **No custom retry/backoff logic is written.** Any `Result.retry()` relies entirely on WorkManager's own
  built-in exponential backoff policy — this keeps the worker itself simple and avoids re-implementing a
  retry policy WorkManager already provides and tests.
- `MmoApplication` implements `Configuration.Provider`, supplying a `Configuration` built from the injected
  `HiltWorkerFactory`, so `CatchRecordSyncWorker`'s constructor-injected dependencies
  (`CatchRecordDraftRepository`, `CatchRecordSubmissionRepository`) are resolved the same way as any other
  Hilt-injected class. This requires removing the default `androidx.startup.InitializationProvider`'s
  `WorkManagerInitializer` from the manifest (`tools:node="remove"`) so WorkManager is initialized
  on-demand from the custom `Configuration` instead of eagerly via the default initializer — this is
  mandatory for the custom `Configuration` to take effect and is enforced by Android Lint's
  `RemoveWorkManagerInitializer` check.

Alternatives considered:

- **A custom `AlarmManager` + `BroadcastReceiver` poll loop** — rejected: reinvents constraint-awareness
  (network state), backoff, and doze-mode/battery-optimisation compliance that WorkManager already handles;
  significantly more code and more edge cases to get right and test.
- **A foreground service that stays alive until connectivity returns** — rejected: unnecessary persistent
  notification/battery cost for what is fundamentally a "run once when a condition becomes true" job; not
  the right tool for a single deferred one-shot action.
- **Retrying only in-memory/in-process (e.g. on next app foreground)** — rejected: does not satisfy
  "sent automatically... when you next have a mobile signal or Wi-Fi connection" without the user having to
  reopen the app; also does not survive process death, which WorkManager does.

## Consequences

- `androidx.work` and `androidx.hilt:hilt-work` are new dependencies, and `CatchRecordSyncWorker` is the
  first `CoroutineWorker` in the codebase — any future background-sync need (in particular, Phase 6/7
  landing-storage/dependent-data invalidation sync, if it needs one) should reuse this same
  scheduler/worker/manifest pattern rather than introducing a second background-scheduling mechanism.
- The manifest's `WorkManagerInitializer` removal + custom `Configuration.Provider` wiring is a one-time,
  easy-to-forget step; any future worker added to this app does **not** need to repeat it (one
  `Configuration.Provider` covers the whole app), but removing/breaking that wiring will silently disable
  Hilt injection into every worker, not just this one.
- `CatchRecordSyncWorker`'s "re-fetch the draft and no-op if it's no longer `PendingSync`" guard is the only
  safeguard against a duplicate/non-idempotent submission on retry (e.g. after process death immediately
  following a successful submit but before the status update landed) — this relies on
  `CatchRecordSubmissionRepository.submit` itself being safe to call at most meaningfully once per draft in
  practice; if a real backend submission endpoint is added later, it should be verified as idempotent (e.g.
  keyed by `catchRecordReference`) or this guard strengthened.
- This is a stub submission repository today (per ADR-0008's precedent of stub-first reference data) — when
  a real submission API is introduced, this ADR's WorkManager scheduling/retry design should still hold;
  only `CatchRecordSubmissionRepository`'s implementation changes.
