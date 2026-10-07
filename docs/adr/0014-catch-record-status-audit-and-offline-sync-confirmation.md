# 0014 - Catch-record status, audit timestamps and offline-sync confirmation

## Status

Accepted (Phase A, Phase B and Phase C).

## Context

CRAR-152 ("offline-first drafts, retry & sync for fishers with no signal") requires FR10 audit
date-times (created/last updated/submission/synchronisation), a user-facing status model that matches the
ticket's real states (not the placeholder `Unsent`/`Amended`/`Late` set), and a fix to a BR-XX violation
where `CatchRecordFlowViewModel.vesselSelected()` persisted an empty draft row immediately on vessel
selection, before the user had entered anything or pressed "Save and continue".

## Decision (Phase A)

### Consolidated status model

`CatchRecordStatus { SUBMITTED, AMENDED, UNSENT, LATE }` (`feature/home/domain/HomeDomain.kt`) is deleted.
`AMENDED` and `LATE` do not correspond to any real Phase A/B state and are dropped rather than carried
forward as dead constants. A new `common/design/RecordStatusTag { Draft, ReadyToSubmit, AwaitingSync,
Submitted }` replaces it, and `DraftStatus.toRecordStatusTag()` (`feature/home/presentation/
RecordStatusMapping.kt`) maps the persisted `DraftStatus` to it 1:1, with `Discarded` mapping to `null`
(filtered from the records list). Placing the tag enum in `common.design` also removes a pre-existing
layering smell where `common/design/CommonComponents.kt` imported from `feature.home.domain`; the
dependency now runs the correct direction (`feature.home` depends on `common.design`).

### `PendingSync` keeps its persisted name, displays as "Waiting to send"

The persisted `DraftStatus.PendingSync` enum constant is not renamed — renaming a persisted enum-backed
column value is a migration/compatibility risk for no behavioural gain. Only the **display** label differs:
GDS plain-English guidance favours "Waiting to send" over the ticket's literal "Awaiting sync" wording, so
`RecordStatusTag.AwaitingSync` is shown to the user as "Waiting to send" (`status_awaiting_sync_title`).

### Three nullable audit columns, no `DEFAULT`

`DraftEntity`/`CatchRecordDraft` gain `createdAtEpochMillis`, `submittedAtEpochMillis`,
`syncedAtEpochMillis: Long?`, all with no `@ColumnInfo(defaultValue = ...)`. A `DEFAULT 0` timestamp reads
back as 1 Jan 1970 — a silently-wrong audit value on a regulatory record is worse than a visibly-absent
(`null`) one. `MIGRATION_6_7` backfills `createdAtEpochMillis` from the only pre-existing timestamp,
`modifiedAtEpochMillis`, for every pre-v7 row: this is a safe **upper bound** on the true creation time (a
row cannot have been created after it was last modified), whereas `submittedAtEpochMillis`/
`syncedAtEpochMillis` are left `null` — a pre-v7 row genuinely has no recorded submission/sync time, and
inventing one would corrupt the audit trail. `ADD COLUMN` does not touch the existing partial unique index
(`index_catch_record_draft_active_vessel`, ADR 0010) or the `Index(["vesselId","status"])`, so no index
work was needed; `fallbackToDestructiveMigration` was not reintroduced (ADR 0010).

### `submittedAt` (user action) vs `syncedAt` (acknowledgement) — deliberately different instants

`RoomCatchRecordDraftRepository.saveDraft` stamps `submittedAtEpochMillis` the first time an incoming draft
reaches `PendingSync` **or** `Submitted` (the moment the user accepted the declaration), and
`syncedAtEpochMillis` only the first time a draft reaches `Submitted` (the moment the backend acknowledged
it). For an **online** submission both land in the same `saveDraft` call and therefore the same instant.
For an **offline** submission, the draft first becomes `PendingSync` (stamping `submittedAt`, `syncedAt`
still null) and only later, once `CatchRecordSyncWorker` (ADR 0009) succeeds, transitions to `Submitted`
(stamping `syncedAt`). Both fields are stamped once and never overwritten by a later `saveDraft`.

### `PendingSync` is not "active"

`DraftStatus.isActive` remains `Draft || ReadyToSubmit` — `PendingSync` deliberately does **not** count.
The v6 partial unique index predicate (`WHERE status IN ('Draft','ReadyToSubmit')`, ADR 0010) is unchanged.
If `PendingSync` counted as active, a fisher with no signal would be blocked from starting a second trip's
draft for the same vessel until connectivity returned and the queued submission synced — defeating this
ticket's entire purpose. A vessel can therefore have one `PendingSync` record awaiting sync **and** a new
active `Draft` in progress at the same time.

### Draft materialisation moved to the first "Save and continue" (BR-XX)

Previously `CatchRecordFlowViewModel.vesselSelected()` called `startDraft(vesselId)`, which inserted a row
immediately — so an abandoned vessel selection left an empty draft polluting the records list, violating
BR-XX ("a record is only a Draft once the user explicitly taps Save and continue"). `vesselSelected` now
only reads (`getActiveDraft`); if none exists it builds an **unpersisted** candidate via the new pure
`CatchRecordDraftFactory.newDraft(...)` and holds it in `CatchRecordFlowViewState.isDraftPersisted = false`
with zero database writes. `CatchRecordDraftRepository.startDraft` changed signature from
`startDraft(vesselId: String)` to `startDraft(candidate: CatchRecordDraft)`, moving candidate construction
to the caller while keeping the exact ADR 0010 race-safe `findOrCreateActiveDraft` path, its partial unique
index, and its lost-race-returns-the-winner semantics unchanged — only the call site moved. `saveAndContinue`
calls `startDraft` once, on the first save only, then proceeds via the existing `saveDraft` path on every
subsequent save; `persistDraftWithoutStepChange`/`persistGearEdit` guard with an early return if
`!isDraftPersisted`, since both are only reachable after a first save in practice.

`catchRecordReference` is still generated at candidate-build time (the ticket's "when you started" framing)
while `createdAtEpochMillis` is stamped at first persist — these are deliberately different instants: a
reference is assigned the moment the user picks a vessel, but the record is not a Draft, and has no audit
creation time, until they choose to keep it.

### Presentation-layer import of `CatchRecordReferenceGenerator`

`CatchRecordFlowViewModel` (presentation) now imports `CatchRecordReferenceGenerator` from
`data.local` directly to build the unpersisted candidate. This is a minor, flagged layering deviation with
existing precedent (`core.error.SafeErrorMapper` already imports `DraftMappingException` from the same
package) — the generator was not relocated to the domain layer in order to preserve that layer's existing
"no `java.time`" convention.

### Incidental WCAG contrast fix

The former `Amended` tag's light-blue background (`0xFFE1EDF7`) paired with `MmoColors.GovBlue`
(`0xFF1D70B8`) text measured ~4.35:1 contrast, just under the WCAG AA 4.5:1 threshold. The new
`ReadyToSubmit` tag reuses the same background but pairs it with `MmoColors.GovBlueHover` (`0xFF003078`),
measuring ~10.42:1.

## Consequences

- Later phases (manual retry, the sync sweep, `ExistingWorkPolicy`, the debug failure toggle, the
  unsaved-changes dialog, the FR9 banner) build on `isDraftPersisted` and the `RecordStatusTag`/
  `DraftStatus` split introduced here; append their decisions as new sections below rather than editing the
  Phase A sections above.
- `RoomHomeRepository`/the records-list `Flow` DAO query remain out of scope for Phase A; `FakeHomeRepository`
  continues to stand in until a later phase wires the real repository.

## Decision (Phase B)

### Flat list projection, not the full aggregate

`CatchRecordDraftDao.observeRecordSummaries()` is a single flat `@Query` (`DraftSummaryRow`), not the
`@Transaction`/`@Relation` aggregate `getDraftWithChildren` uses. For N records that aggregate is N×5 queries
plus full object-graph mapping on every emission, on an encrypted (SQLCipher) database, to render ~5 fields
per row. `DraftSummaryMappers.toDomain` maps each row to a `CatchRecordSummary`, returning `null` (filtered
by `mapNotNull`) for an unrecognised/`Discarded` status — belt-and-braces alongside the SQL
`status != 'Discarded'` predicate. The full aggregate is still loaded on demand only when a draft is resumed
(`getDraftById`). Sort is `modifiedAtEpochMillis DESC, id DESC`, the `id` tiebreak making test ordering
deterministic when two rows share a timestamp.

### `HomeRepository` becomes reactive; `HomeSummary` drops pagination

`HomeRepository.observeSummary(): Flow<HomeSummary>` replaces the one-shot `suspend fun getSummary():
Result<HomeSummary>` — a single read cannot reflect a background `CatchRecordSyncWorker` transition
(`PendingSync` → `Submitted`) without the user manually refreshing, which defeats FR7/FR9. `HomeViewModel`
collects this flow for its lifetime in `init` rather than exposing a one-shot `load()`. `HomeSummary` drops
`totalCount`/`pageStart`/`pageEnd` (the list is local-only, bounded to one fisher's own device records — see
`PaginationBar` removal below); `pendingCatchRecordCount` is now a derived getter
(`count { status == AwaitingSync }`) rather than a stored field, so it can never drift from the list it
summarises. `FakeHomeRepository` (`main`) is deleted; a hand-written, colocated `src/test` fake
(`MutableSharedFlow<Result<HomeSummary>>(replay = 1)`) replaces it for `HomeViewModelTests`.

### Stacked rows, not the 4-column table — recorded DesignSystem deviation

`CatchRecordsTableSection` is replaced by `catchRecordsListSection`, rendering each record as a stacked
GOV.UK-style row in a `LazyColumn` rather than a 4-column table. With a Retry button added for `AwaitingSync`
rows, a fixed-column table cannot meet WCAG 1.4.4 (200% text reflow) or 1.4.10 (reflow at ~320dp) without
clipping a column. There is no Figma reference for this screen; the stacked layout is a plan-approved,
written-brief deviation from both the prior table and the DesignSystem's default table component — recorded
here per the figma-design instructions' deviation-register requirement. The "Created by" column is dropped
entirely (it never had real data behind stub sign-in); the catch record reference becomes the row's
`heading()` instead.

### `LazyColumn` replaces `Modifier.verticalScroll` (R14)

`HomeTabContent` wrapped its content in `Modifier.verticalScroll`, which cannot contain a nested
`LazyColumn` (it crashes at runtime). `HomeTabContent` is restructured into a single top-level `LazyColumn`
with the banner/heading/accordions as `item {}` blocks and the records via `catchRecordsListSection`.

### Touch targets: `LocalMinimumInteractiveComponentSize`, not `Spacing.minTouchTarget`

Rows and the Retry button use `sizeIn(minWidth/minHeight = LocalMinimumInteractiveComponentSize.current)`
per the approved brief, rather than the codebase's prevailing `Spacing.minTouchTarget` constant used
elsewhere (e.g. `PrimaryActionButton`). Both currently resolve to 48dp; this is a flagged, minor
inconsistency rather than a regression — a future cleanup could fold `Spacing.minTouchTarget` to delegate to
the same Material3 local so there is one source of truth.

### Draft recovery is list-selectable, not most-recent-only (FR2)

`DraftResumeRoute` gains an optional `draftId`; `CatchRecordFlowViewModel.enterFlowForDraft(draftId)` loads
the chosen draft via `getDraftById` (erroring, terminal/non-retryable, if not found) instead of always
loading via `getAnyActiveDraft`. A row tap for a `Draft`/`ReadyToSubmit` record navigates straight into this
route; `enterFlow()`'s existing-draft branch is unchanged (used only for the "resume whatever's active" entry
point, e.g. the wizard's own re-entry). Both submission screens' "View your catch records" actions now land
on the records list instead of plain Home.

### Cross-package import: `data.local` → `home.domain`

`DraftSummaryMappers` (in `feature.catchrecord.data.local`) imports `CatchRecordSummary` and
`toRecordStatusTag` from `feature.home.domain`/`feature.home.presentation`. This mirrors the existing Phase A
precedent of `common.design` depending on `feature.home` concepts being corrected the other way; here the
catch-record data layer depends on the home feature's domain shape because the summary projection is
inherently Home-screen-shaped. Flagged as a minor layering coupling, not reversed, because splitting
`CatchRecordSummary` into a separate shared module was judged disproportionate for Phase B's scope.

### `PaginationBar` removed; related dead strings cleaned up

`PaginationBar(onNextClick = {})` was a visibly interactive control that did nothing — replaced by a plain
count line (`catch_records_count`). `showing_x_to_y_of_z` and `next` are deleted per the brief; `col_created_by`
and `col_status` are additionally deleted (beyond the brief's literal wording) since the table they belonged
to no longer exists and Android Lint's unused-resource check would otherwise fail `lint`.

### Offline banner promoted to the Home `Scaffold` (FR4)

`OfflineBanner` itself is unchanged (its `liveRegion = Polite` semantics and `testTag("offline_banner")`
already satisfied WCAG 4.1.3). It now renders once in `HomeScreenContent`'s `Scaffold` body, driven by
`ConnectivityViewModel.isOffline`, covering all three Home tabs — previously it was wizard-only.

## Consequences (Phase B)

- Phase C's manual retry wires `catchRecordsListSection`/`CatchRecordRow`'s existing `onRetry: (String) ->
  Unit` no-op parameter to real retry logic; no further UI changes to the row should be needed.
- `HomeScreenRobolectricTests` is the Kover/SonarCloud-reachable Compose coverage for the records list and
  offline banner, since `connectedDebugAndroidTest` coverage is not merged into that gate.
- `RoomCatchRecordDraftRepository`/`RoomHomeRepository` are the shipped Phase B implementations;
  `FakeHomeRepository` remains only in `src/test`.

## Decision (Phase C)

### `ExistingWorkPolicy.REPLACE` → `KEEP` (see ADR 0009 amendment)

Moved to ADR 0009, since it is that ADR's decision being corrected, not a new one. In summary: `KEEP` is a
duplicate-submission correctness fix, not a style preference, and it is what lets both the manual retry use
case and the reconciliation sweep below skip any `getWorkInfosForUniqueWork` guard.

### App-start reconciliation sweep is domain-state reconciliation, not WorkManager distrust (FR7)

WorkManager already persists enqueued work across process death, device reboot (its own `RescheduleReceiver`
— confirmed present in the merged manifest, see Consequences) and force-stop (`ForceStopRunnable`).
`CatchRecordSyncSweep` is not a redundant re-implementation of that. It exists because writing
`status = PendingSync` to Room and enqueuing the WorkManager request are **not one atomic transaction**, so
a row can be stranded at `PendingSync` with no in-flight (or ever-scheduled) work in four scenarios: (1) the
process dies between `saveDraft(PendingSync)` and `scheduleSync` in `CatchRecordFlowViewModel`; (2) the
worker returned `Result.failure()` on validation, leaving the row stuck with no further retries scheduled;
(3) WorkManager pruned its own completed-work record, which is unrelated to the Room row's status; (4) the
record predates this feature (a draft already `PendingSync` before the sweep/scheduler existed). `KEEP`
makes the sweep's re-enqueue of an already-enqueued/running unique name a safe no-op, so no
`getWorkInfosForUniqueWork` check is needed before calling `scheduleSync`.

### Sweep dependency resolution is deferred via `dagger.Lazy`, not resolved at Hilt field-injection time

`CatchRecordSyncSweep` is field-injected eagerly into `MmoApplication` so it runs once per process, but its
constructor takes `Lazy<CatchRecordDraftDao>`, not `CatchRecordDraftDao` directly. Hilt's field injection
of `MmoApplication` happens synchronously, on the calling thread, as part of `super.onCreate()` — resolving
the real (SQLCipher) `CatchRecordDatabase` at that point, even indirectly, violates both "never on the main
thread" and "must not block onCreate" (R6), since `DatabaseModule.provideCatchRecordDatabase()` calls
`SqlCipherLibraryLoader.ensureLoaded()` (a `System.loadLibrary` call) the moment anything resolves it, not
lazily on first query. Wrapping the DAO dependency in `Lazy` means merely constructing `CatchRecordSyncSweep`
is cheap; the database is only touched inside `sweep()`'s own `applicationScope.launch { }` body, which also
`runCatching`-wraps the DAO resolution so a database failure (or, incidentally, SQLCipher's native library
being entirely absent under a Robolectric/JVM test — see Consequences) is logged via Timber and never
crashes the caller.

### Manual retry (FR8/AC4): always enqueue, double-dispatch guarded by view-state, not debounced in the UI

`RetryCatchRecordSubmissionUseCase` always calls `scheduleSync(draftId)` and only branches on
`NetworkConnectivityChecker.isConnected()` to decide the returned result (`Enqueued` vs
`EnqueuedWhileOffline`) for messaging — retrying while offline is not a no-op, since the `CONNECTED`
constraint on the already-enqueued request is what defers it, and that is the truthful, FR7-consistent
behaviour to describe to the user (`records_retry_offline_message`). Double-tap protection is two layers:
`KEEP` at the scheduler is the authoritative guard (a repeat `scheduleSync` call for an already
`ENQUEUED`/`RUNNING` unique name is a no-op); `HomeViewState.retryingIds` is a visual-feedback-only layer
that disables the row's Retry button and swaps its label, cleared once the draft's `RecordStatusTag` is
observed to actually change between two consecutive summary emissions (tracked via a
`lastKnownStatusByDraftId` map in `HomeViewModel`). This leaves one known residual edge case: a draft that
fails validation permanently (worker returns `Result.failure()`, not `retry()`) and so never changes status
again would stay visually disabled until the next full-summary-changing event; this is acceptable for Phase
C's scope and is mitigated by the fact the row remaining visually "pending" is not incorrect — the draft
genuinely has no further automatic retry path once validation fails terminally.

### Rejected: `getWorkInfosForUniqueWorkFlow`, expedited work, `ProcessLifecycleOwner`, an `androidx.startup.Initializer`

- **`getWorkInfosForUniqueWorkFlow`** per row — rejected for Phase C: it would need N concurrent flows for N
  rows just to drive the `retryingIds` visual guard, when `KEEP` already makes the guard's correctness
  independent of observing WorkManager's own state. Noted here as the natural upgrade path if a future phase
  wants live per-row progress (e.g. "Sending…" vs "Waiting to send").
- **Expedited work** for the retry/worker request — rejected: it is quota-controlled, can silently downgrade
  to regular work, and on API ≤30 may require a foreground service under Android 14/15's FGS-type rules. A
  short, `CONNECTED`-constrained submission does not justify that cost; WorkManager's default exponential
  backoff is kept as-is.
- **`ProcessLifecycleOwner`** to trigger the sweep — rejected: it fires on every foreground transition, not
  once per process, repeating the encrypted-DB read for no additional benefit over a single app-start sweep.
- **An `androidx.startup.Initializer`** — rejected: needless additional risk/indirection given the app has
  already removed `WorkManagerInitializer` from `InitializationProvider` (ADR 0009); a direct call from
  `MmoApplication.onCreate()` is simpler and consistent with that existing pattern.

### Debug-only submission-failure toggle: build-type source sets, not a `BuildConfig.DEBUG` guard

Secure by Design favours *eliminating* a capability over guarding it. A `BuildConfig.DEBUG` runtime check
still compiles the failure-injection code, its toggle storage and its settings-row UI into the release APK;
R8/ProGuard shrinking would *probably* strip the now-dead branch, but that is a best-effort optimisation, not
a guarantee, and anything present in the binary is reachable from a repackaged/re-signed APK or an attached
debugger. `app/src/debug` and `app/src/release` (both new) instead mean the release variant's compiled
classes **do not contain** `FailureInjectingSubmissionRepository`/`DebugSubmissionFailureToggle`/
`DebugSettingsSectionImpl` at all — verified directly against the assembled release artifact (R11), not
inferred from source. The in-memory `MutableStateFlow<Boolean>` toggle (rather than DataStore) cannot persist
across restarts and keeps the debug source set free of any additional storage dependency.

### R15 — OEM battery managers can still suppress background work

Some OEM battery-optimisation layers (e.g. manufacturer-specific "battery saver"/app-hibernation features
beyond stock Android Doze) can suppress or delay WorkManager jobs regardless of constraints being met, and
this is not solvable from inside the app. FR8's manual retry, this sweep, and FR6's honest "will be sent
automatically when you're back online" copy are the available mitigations — a user is never left with a
silently-stuck record and no way to act on it.

## Consequences (Phase C)

- The merged debug manifest (`:app:processDebugMainManifest`) was inspected directly and confirms WorkManager's
  reboot machinery survived the `WorkManagerInitializer` removal: `android.permission.RECEIVE_BOOT_COMPLETED`
  and the `androidx.work.impl.background.systemalarm.RescheduleReceiver` (with its `BOOT_COMPLETED` intent
  filter) are both present — only the `WorkManagerInitializer` `<meta-data>` entry under
  `InitializationProvider` was removed, which is the intended, narrowly-scoped effect of that `tools:node`.
- `FakeCatchRecordDraftRepository.saveDraft` (test-only) now mirrors `RoomCatchRecordDraftRepository`'s
  `submittedAt`/`syncedAt` stamping, so `CatchRecordSyncWorkerTests` can assert AC3 end-to-end without a real
  Room database; this was a pre-existing gap (the fake previously stored drafts verbatim).
- `CatchRecordSyncSweepTests`/`RoomCatchRecordDraftRepositoryTests` use a plain unencrypted
  `Room.inMemoryDatabaseBuilder` (no SQLCipher, no Hilt) under Robolectric — SQLCipher's native library is
  genuinely unavailable on a JVM test run, which is also why `CatchRecordMigrationTest` must be an
  `androidTest`, not a Robolectric test (see that test's own doc comment).
- `CatchRecordMigrationTest` applies each `Migration.migrate(db)` directly rather than via
  `MigrationTestHelper.runMigrationsAndValidate`: that helper reads the live on-disk `TableInfo` — including
  indices — and compares it to Room's annotation-derived expected schema, so the moment a seeded v6 database
  genuinely carries `index_catch_record_draft_active_vessel` (as any real post-ADR-0010 device does), the
  comparison fails with "Migration didn't properly handle: catch_record_draft", reporting the correctly
  preserved raw index as an unexpected extra. This is independent, concrete proof of R3 (Room's schema
  tooling cannot see this index) rather than just a theoretical risk — explicit raw-SQL row/index assertions
  are therefore not optional belt-and-braces, they are the **only** way to validate this migration.
- Phase D (the unsaved-changes dialog, the pre-submit offline notice) and Phase E (the FR9 sync-confirmation
  banner, the DataStore watermark) build on this phase's `retryingIds`/offline-message plumbing in
  `HomeViewState` and the now-correct `KEEP` policy; append their decisions as new sections below.
