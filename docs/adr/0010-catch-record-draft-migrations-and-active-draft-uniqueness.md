# 0010 - Catch-record draft Room migrations and enforced active-draft uniqueness

## Status

Accepted

## Context

ADR 0006 introduced real Room entities for the catch-record draft aggregate but deferred writing real
`Migration`s across the v1 -> v5 schema bumps, instead relying on
`RoomDatabase.Builder.fallbackToDestructiveMigration(dropAllTables = true)` on the grounds that "Pre-release
app, no production data to preserve yet". A comprehensive remediation review (E4-S01 follow-up) found this
now unacceptably deletes a fisher's in-progress draft (the offline-first "Create a Catch Record" journey's
entire reason for existing) on every app update that bumps the schema — a real, observable data-loss defect,
not a Stage-1 simplification.

Separately, ADR 0006 documented "one active (non-terminal-status) draft per vessel" as enforced only "at the
repository layer, inside the same transaction that looks up/creates a draft", because "Room's annotation-based
indices cannot express a partial/conditional `WHERE status IN (...)` unique constraint". The review found this
was not actually transactionally race-safe: `RoomCatchRecordDraftRepository.startDraft` performed a
read-then-conditionally-insert with no database-level constraint backing it, so two concurrent calls (e.g. a
resumed process racing a fresh entry) could both observe "no existing active draft" and both insert one,
producing two active drafts for the same vessel with no error.

## Decision

1. **Write real `Migration`s for every schema step, v1 through v6**, using the committed `app/schemas` JSON
   history (v1..v5) as the authoritative source of each version's actual column/table shape, and remove
   `fallbackToDestructiveMigration` entirely from `di/DatabaseModule.kt`. `CatchRecordDatabase` is registered
   with `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)`. A draft
   in progress must survive every future app update; destructive recreation is no longer an acceptable
   default for this database.
2. **v6 adds a genuine partial unique index** enforcing "one active draft per vessel" at the SQLite level:
   `CREATE UNIQUE INDEX ... ON catch_record_draft (vesselId) WHERE status IN ('Draft', 'ReadyToSubmit')` —
   matching the literal persisted enum-name strings written by `DraftEntity.status` (see
   `DraftMappers`/`CatchRecordDraftDao`'s existing `ACTIVE_STATUSES_CLAUSE`), which Room's `@Index`
   annotation cannot express (no `WHERE` support) but raw migration SQL can.
3. Because that index would fail to create if any pre-existing installs already have duplicate active drafts
   per vessel (impossible to fully rule out once this ships), **`MIGRATION_5_6` first deduplicates**: for
   every `vesselId` with more than one row whose `status` is `Draft`/`ReadyToSubmit`, it keeps the
   most-recently-modified (`modifiedAtEpochMillis`, then `id`, tie-broken deterministically) and demotes every
   other duplicate's `status` to `Discarded` — never deletes rows outright, preserving the data for audit while
   removing it from "active" so the subsequent `CREATE UNIQUE INDEX` never fails. This is implemented in plain
   Kotlin (a query + per-row `UPDATE`) rather than a single SQL statement using window functions, so it does
   not depend on the specific SQLite/SQLCipher build's window-function support and is straightforward to unit
   test against seeded violating rows.
4. **`CatchRecordDraftDao` gains a race-safe find-or-create path**: `insertDraftIfAbsent` uses
   `@Insert(onConflict = OnConflictStrategy.IGNORE)` (deliberately *not* `REPLACE`, which would silently delete
   a concurrently-created draft's cascaded child rows), and a new `@Transaction`-wrapped
   `findOrCreateActiveDraft(vesselId, candidate)` re-checks for an existing active draft, attempts the
   ignore-on-conflict insert, and — if the insert was ignored because the new unique index already has a row
   for this vessel (lost the race, or a concurrent caller won it first) — re-queries and returns that existing
   row instead of raising an error or duplicating. Wrapping the whole find+insert in one Room `@Transaction`
   relies on SQLite's write-transaction serialization to make the check-then-act atomic across concurrent
   callers, and `RoomCatchRecordDraftRepository.startDraft` now calls this single DAO method instead of its own
   non-atomic read-then-insert.

## Consequences

- `app/schemas/.../6.json` is committed alongside the new migrations, per Room's existing schema-export setup
  (ADR 0003/0006).
- Every future schema change to `CatchRecordDatabase` must add a real `Migration` (verified by a
  `MigrationTestHelper`-based test asserting no data loss) rather than reaching for
  `fallbackToDestructiveMigration` again; this ADR's migration chain is the template to extend.
- The v6 dedupe is a one-time, best-effort recovery for any already-corrupted local state; going forward the
  unique index (and the race-safe DAO path) prevents the duplicate-active-draft condition from recurring.
- `FR10`-style dependent-data invalidation (clearing a gear's stat-rectangle/species when it is unconfirmed or
  its rectangle changes) remains the wizard flow `ViewModel`'s reducer responsibility per ADR 0007 — this ADR
  only concerns schema evolution and the active-draft uniqueness constraint, not invalidation semantics.
