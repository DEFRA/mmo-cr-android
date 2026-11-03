# 0018 - Catch-record drafts are independent; vessel selection never adopts an existing draft

## Status

Accepted

## Context

[ADR 0006](0006-catch-record-draft-persistence-and-encryption.md) and
[ADR 0010](0010-catch-record-draft-migrations-and-active-draft-uniqueness.md) established an invariant of
**one active (non-terminal) draft per vessel**, enforced by a v6 partial unique index
(`CREATE UNIQUE INDEX index_catch_record_draft_active_vessel ON catch_record_draft (vesselId) WHERE status
IN ('Draft', 'ReadyToSubmit')`) plus a `@Transaction`-wrapped `findOrCreateActiveDraft` DAO method. As a
consequence, `CatchRecordFlowViewModel.vesselSelected` *adopted* an existing active draft for the chosen
vessel instead of creating a new one.

Three problems with that:

1. **It is wrong for the domain.** A fisher routinely has more than one trip in flight for the same vessel —
   a trip recorded but not yet submitted, and a new trip starting. Collapsing them onto one draft per vessel
   is a modelling error, not a safety feature.
2. **It silently destroyed user data.** "Create a new catch record" → pick a vessel → the wizard quietly
   resumed a *different*, half-finished trip. Answering "Is this trip today?" then overwrote that older
   trip's departure and return dates with today's. This was raised as finding **H2** in the code review of
   the preceding draft-resume fix and deliberately deferred to this change.
3. **The index never actually existed on any working install.** Room 2.8.5's `TableInfo.readIndices` keeps
   `PRAGMA index_list` rows with `origin == "c"`, which includes a raw partial index, and
   `TableInfo.equalsCommon` compares indices by set equality. A database that genuinely ran `MIGRATION_5_6`
   would therefore fail `onValidateSchema` and crash on open. `app/schemas/.../5.json` and `6.json` are
   byte-identical apart from `"version"` (identical `identityHash` `7a5ec28b10a29b9c8347e666760b5049`), so
   v6 carried **zero** Room-visible schema delta. In practice one-draft-per-vessel was enforced only in
   Kotlin, by `findOrCreateActiveDraft`.

## Decision

1. **Drafts are independent.** A vessel may hold any number of simultaneous non-terminal drafts. There is no
   "the active draft for vessel X" concept anywhere in the domain, data or presentation layers. The only
   uniqueness invariant that survives is the **v10 unique index on `catchRecordReference`** (the Trip ID).
2. **Selecting a vessel always creates a new draft.** `CatchRecordFlowViewModel.vesselSelected` calls
   `startNewDraft(vesselId)` unconditionally. An existing draft is resumed only by explicitly tapping its
   row in the Home records list (`enterFlowForDraft`). A `UiStatus.Loading` guard makes a double tap
   idempotent.
3. **`MIGRATION_10_11` drops the index** with `DROP INDEX IF EXISTS index_catch_record_draft_active_vessel`.
   `IF EXISTS` is required precisely because, per the Context above, most installs never had it.
4. **`MIGRATION_5_6` is neutralised to a no-op.** Re-running the historic dedupe would demote a perfectly
   legitimate second draft to `Discarded` on an upgrading device, and creating the index would break Room's
   schema validation. The migration is retained (never renumbered or deleted) so the v1..v11 chain stays
   contiguous for installs that have not yet upgraded.
5. **`findOrCreateActiveDraft`, `findActiveDraftEntity`, `getActiveDraftWithChildren` and
   `CatchRecordDraftRepository.getActiveDraft` are deleted**, replaced by a `@Transaction`-wrapped
   `CatchRecordDraftDao.createDraft(candidate)` that fails fast on a duplicate id and bumps the Trip ID's
   timestamp by one second (up to `MAX_REFERENCE_ATTEMPTS`) on a reference collision. This amends ADR 0010
   decision 4: `insertDraftIfAbsent` keeps `OnConflictStrategy.IGNORE`, but it now guards only the Trip ID.
6. **The Home records list must distinguish same-vessel drafts.** Each row renders its Trip ID as a second
   line under the trip end date and exposes a composed accessible name
   (`"<date>, <vessel>, <status>, Trip ID <reference>"`), falling back to "Not assigned yet" when no
   reference exists.

## Consequences

- **Downgrades crash.** Once a device opens the database at v11, an older build expecting v10 will fail to
  open it. Room offers no downgrade path here; releases roll forward only.
- **Abandoned drafts accumulate.** Backing out of the wizard after picking a vessel, then picking the same
  vessel again, now produces a second empty draft rather than reusing the first. This is accepted for now;
  a "discard an untouched draft on back-out" behaviour is a candidate follow-up.
- **Accessibility (WCAG 2.2 AA) becomes load-bearing.** With several same-vessel rows, the trip end date
  alone is no longer a unique link name. The composed `contentDescription` satisfies SC 2.4.4 Link Purpose
  and SC 4.1.2 Name, Role, Value, and leads with the visible text to satisfy SC 2.5.3 Label in Name. The
  Trip ID second line renders in `MmoColors.Grey1` (`#6F777B`), ~4.56:1 on white — above the 4.5:1 required
  by SC 1.4.3 Contrast (Minimum).
- **Sync is unaffected.** `WorkManagerCatchRecordSyncScheduler` already keys unique work on the draft id
  (`catch-record-sync-$draftId`), not the vessel, so concurrent same-vessel submissions do not collide.
- **A divergence is left open.** `enterFlowForDraft` reconciles against the *unbanded* `getGearTypes()`
  while `vesselSelected` emits the vessel-length-*banded* `getGearTypes(vesselLength)`. With resume now
  confined to `enterFlowForDraft` this no longer causes a reconcile/display mismatch, but the two paths
  still show different gear lists for the same draft. Tracked as a separate follow-up ticket.
- `app/schemas/.../11.json` is committed alongside the migration, per ADR 0003/0006.
