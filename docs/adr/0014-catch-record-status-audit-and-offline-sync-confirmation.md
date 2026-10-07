# 0014 - Catch-record status, audit timestamps and offline-sync confirmation

## Status

Accepted (Phase A).

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

