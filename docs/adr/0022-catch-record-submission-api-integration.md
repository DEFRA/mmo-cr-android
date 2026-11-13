# 0022 - Catch record submission API integration

## Status

Accepted

## Context

`CatchRecordSubmissionRepository.submit(draft)` has only ever been backed by
`StubCatchRecordSubmissionRepository` (ADR 0009): it returns `Result.success(Unit)` without making a network
call, so no completed catch record has ever actually left the device. A real
`mmo-cr-catch-recording-service` now exists, documented by its OpenAPI contract
(`catch-recording-openapi.yaml`). Submitting a catch record to it is a **three-step, resumable** sequence —
`POST /v1/catch-records` (create the DRAFT) → `PUT /v1/catch-records/{id}` (complete replacement of the
client-owned sections) → `POST /v1/catch-records/{id}/submission` (submit) — each step requiring the
previous step's server id/version, so a process death or lost response mid-sequence must resume without
re-creating a server record for a regulatory submission that may already have landed.

This ADR covers the full integration design (the approved plan's Phases 1-7). Phases 0-2 landed first (ADR,
Room v11→v12 migration and reference-format switch, network layer not yet wired into app behaviour); Phases
3-7 (mapper, repository, worker/status, UI, Hilt wiring) landed once the blocking backend questions below
were answered.

## Decision

### Three-step resumable sequence, state persisted before every call

Each logical submission mints two idempotency keys **once**, not per attempt: `createIdempotencyKey` (reused
across every retry of the `POST /v1/catch-records` call) and `submitIdempotencyKey` (reused across every
retry of the `POST .../submission` call). Both keys, the server-assigned `catchRecordId`/`version`, and a
`submissionStep` cursor (`None` → `Created` → `Replaced` → `Submitted`) are **persisted to Room before the
network call they protect is made**, so a process death at any point resumes from the last durable state
rather than silently re-attempting a step whose server-side effect already committed. This is the same
"persist then act" discipline ADR 0014 established for audit timestamps, now extended to submission
progress.

### Reference format switch: `GBR-{PREFIX}-{ddMMyy}-{HHmmss}`

The server's `catchRecordReference` validates against `^GBR-[A-Z0-9]+-\d{6}-\d{6}$` — a `GBR-` prefix and
a 6-digit (not 8-digit) date. `CatchRecordReferenceGenerator` switches to this format, and Room's v11→v12
migration **deterministically rewrites every existing local reference** (`{PREFIX}-DDMMYYYY-HHMMSS` →
`GBR-{PREFIX}-DDMMYY-HHMMSS`, a pure substring transform dropping the century digits) rather than
regenerating at submit time. This is judged safe now, and only now: nothing has ever left the device (the
repository has only ever been the stub), so no reference has ever been shown to MMO, while the reference
*is* already shown to the fisher (GDS confirmation-page guidance requires a stable reference once shown).
Rows that do not match the legacy pattern are left untouched. This is recorded as risk R-4.

The rewrite is pure SQL (no row-by-row Kotlin loop, unlike v9→v10's dedupe): legacy references are
matched with SQLite `GLOB` (case-sensitive, unlike `LIKE`) against the fixed 16-character
`-DDMMYYYY-HHMMSS` suffix, then rewritten with `substr` by position from the end of the string,
dropping the century digits. There is no collision risk — prefixing every legacy reference with the
same literal `GBR-` cannot introduce a duplicate that did not already exist as a legacy duplicate,
and v9→v10 already guarantees uniqueness.

**OQ-4 (accepted):** the app persists no RSS number for a vessel, only a PLN (`Vessel.pln`). The reference
is sent as `GBR-{normalised PLN}-…`, which passes the server's regex; raised with the backend/reference-data
team as a follow-up (no RSS is available to mobile today).

### Retry/terminal classification and the new `SubmissionFailed` status (Phase 5)

A real backend can return terminal 4xx responses that must not be retried forever — unlike the stub, which
never failed. §6 of the approved plan defines the full classification table (transient → `Result.retry()` →
`PendingSync`; terminal → `Result.failure()` → a new `DraftStatus.SubmissionFailed` with a typed
`SubmissionFailureReason`). This requires an **ADR 0009 amendment** (below): a `runAttemptCount` cap is now
mandatory, because ADR 0009's "no custom retry/backoff logic" assumed every failure was transient.

### Recovery reads (OQ-1, accepted)

`GET /v1/catch-records` and `GET /v1/catch-records/{id}` are added to the API surface, for **read-only
recovery use only** (never a general listing/history UI): a `409 DUPLICATE_RESOURCE` on create or a lost
response needs a way to discover the record's actual server state before deciding whether to resume,
adopt, or fail. Without this read-back, a lost response is permanently unrecoverable.

### Stub retained for `local`; logging redaction extended

The stub submission repository is retained for the `local` flavor (no backend to call locally without the
Docker stack). `Idempotency-Key`, `If-Match`, and `ETag` are added to `HttpLoggingInterceptor`'s header
redaction list (ADR 0011 + `security.instructions.md`): none of these three carry PII, but an idempotency
key or version precondition is still a value that should never appear in a shared log, consistent with the
existing `Authorization` redaction.

### Error shape and a Phase 2 status-based `retryable` default

Unlike the reference-data service's nested `{"error": {...}}` envelope with its own `retryable` field, the
catch-recording service's `ErrorResponse` is flat (`statusCode`, `code`, `message`, `correlationId?`,
`details?`) and carries no `retryable` field of its own. `CatchRecordHttpException` derives it from the
HTTP status alone (`>= 500` transient, else terminal) as a Phase 2 placeholder; Phase 5 replaces this with
the full §6 classification table (409 vs 412, etc.).

## Open backend questions — now resolved (2026-11-13)

All blocking questions below were answered by the backend team and are authoritative; Phase 3 (mapper) is
unblocked by this resolution.

| # | Question | Resolution |
|---|---|---|
| OQ-2 | `gears[].statisticalArea.id` is a reference-data row id; the app persists only the sub-rectangle **code**. | It is the **sub-rectangle id**. The app resolves its persisted code (e.g. `38E95`) to the cached `StatisticalSubRectangle` id and snapshot fields; unresolvable → terminal `SubmissionMappingException`. |
| OQ-3 | `characteristics[].characteristicId` (UUID) is currently dropped. | Carried through as `GearMeasurementDto.id`; one entry per gear measurement with `nameSnapshot`/`unitSnapshot`. |
| OQ-4 | No RSS available to mobile, only PLN. | Accepted: `GBR-{PLN}-…` (see above). |
| OQ-5 | `NotLandedSpeciesEntry` holds one weight; unclear which server field it maps to. | Confirmed `weightAboveMinimumKg`. |
| OQ-6 | `trip.dateStarted`/`dateEnded` format and required-ness. | Confirmed `YYYY-MM-DD`, required only when `startedAndFinishedToday=false` (omitted when true). |
| OQ-7 | `gears[].associationId` is required by the canonical schema but absent from the backend's own example. | Backend itself is unsure it is required. Default: **omit**; the mapper keeps the value (`GearUse.id`) trivially switchable to "send" — a 422 naming `associationId` on device verification is the signal to flip it (Phase 7 check). |
| OQ-9 | Idempotency-Key replay-window retention is undocumented. | Backend confirms the key's purpose (de-duplicating same-request double-clicks) but not a retention window. Client still relies on the OQ-1 read-back as a durable fallback. |
| `numberOfShots` | No server field exists for this locally-captured value. | Confirmed: gear values are per-characteristic only, there is no separate field. `numberOfShots` does **not** correspond to any reference-data gear measurement, so it is **not sent** — this is a confirmed data-drop on submission, not a mapping bug. |
| RSS vs PLN | See OQ-4. | — |
| 409 vs 412 (R-5) | RFC 9110 mandates `412` for a failed precondition (`If-Match`); the server uses `409`. | Backend confirms `409` for version mismatch is **intentional**, not a bug. Client treats `409` as the version-conflict signal; a `412` is also accepted harmlessly (same handling) in case any endpoint is later conformed to RFC 9110. |

### Snapshot fields — sent wherever locally resolvable

The backend's newer examples include snapshot fields and the OpenAPI schema requires them (gear, port,
species, statistical area, characteristics). The mapper sends a snapshot wherever the cached reference data
can supply one. No snapshot field was found to be unavailable locally during Phase 3 mapping — every
required snapshot (`codeSnapshot`/`nameSnapshot`/`unitSnapshot`/`faoCodeSnapshot`) has a corresponding
cached reference-data field. Unset weights are **omitted** (not sent as explicit `null`), matching the
backend's own examples; `pairFishing` is the one field that keeps an explicit `null` by contract.

## Risk R-1 (security, backend follow-up, not fixed here)

On 2026-11-13, a probe of the DEV catch-recording and reference-data services showed both return `200` to an
arbitrary non-empty bearer token, including a listing endpoint returning 20 records. **Dev `200`s therefore
do not prove `aud`/`scp`/ownership correctness** — real JWT validation is not yet enforced. This is **not**
fixed client-side: it is raised with the backend team as a follow-up to enforce
`aud=8bc065bf-046f-4d18-834a-753b325e5221`,
`iss=https://da4b4234-9189-4040-ac28-9ae2ff80f2be.ciamlogin.com/da4b4234-9189-4040-ac28-9ae2ff80f2be/v2.0`,
`scp=access_as_user`. All dev auth results are treated as unproven until that lands, and first-run device
verification (Phase 7) must be re-run afterwards.

## Consequences

- The reference format is now server-compatible and has been rewritten once, in Room's v11→v12 migration;
  any future reference-format change must go through the same deliberate, justified, one-time path — it is
  not a transform to repeat casually.
- `Idempotency-Key`/`If-Match`/`ETag` are permanent additions to the logging redaction list; any future
  header carrying a precondition/replay-key value should be added to the same list.
- ADR 0009's "no custom retry logic" is superseded for this worker — see the amendment below — but still
  holds for any *other* future `CoroutineWorker`, until that worker also needs a terminal/transient split.
- `numberOfShots` is a confirmed, permanent data-drop on submission (no server field) — not a bug to fix.
- OQ-7's `associationId` omission is a **device-verification checkpoint**, not a closed decision: a `422`
  naming `associationId` is the trigger to flip the mapper to send `GearUse.id`.

## Amendment — `apiMeasurementId` cache staleness classified Transient, not Terminal (2026-11-20)

`MIGRATION_12_13` added a nullable `apiMeasurementId` column to `reference_data_gear_measurement` but, on
its own, left existing installs' cached gears dataset untouched: `ReferenceDataRefresher` skips re-fetching
a dataset whose manifest version/etag already matches the stored metadata, so the column would otherwise
stay `null` until the backend happens to bump the gears dataset — making every submission with a gear
measurement fail as "Could not send" forever, on every pre-existing install. The migration now also deletes
the `gears:*` rows from `reference_data_metadata`, forcing an unconditional re-fetch on the very next sync.
Separately, the mapper's blank-`apiMeasurementId` failure is reclassified from `SubmissionMappingException`
(Terminal) to a new `UnresolvedGearMeasurementException` subtype (Transient): this specific cause is a
cache-freshness problem, not a genuine data mismatch, so retrying after a reference-data refresh can
resolve it without user action. `RealCatchRecordSubmissionRepository` schedules that refresh itself
(reusing `ReferenceDataRefreshScheduler.scheduleRefreshAfterSignIn()`) whenever this exception is caught,
so the next retry attempt has a real chance of succeeding. The other `SubmissionMappingException` causes
(missing port/gear/species/rectangle, unknown measurement key) remain Terminal — they reflect reference
data genuinely absent from the schema, which a refresh cannot fix.

## Amendment to ADR 0009 — `runAttemptCount` cap and terminal classification (2026-11-13)

ADR 0009 states: *"No custom retry/backoff logic is written. Any `Result.retry()` relies entirely on
WorkManager's own built-in exponential backoff policy."* That held while `CatchRecordSubmissionRepository`
was a stub that never failed. Against a **real** backend, a terminal 4xx (e.g. `403` wrong vessel, `422`
rejected data) would otherwise retry forever under WorkManager's backoff, burning battery and never
surfacing the problem to the fisher.

This is superseded, not reversed: WorkManager's backoff policy is still used as-is for every *transient*
failure (network errors, `5xx`) — no custom backoff/delay logic is introduced. What is added is a
**terminal/transient classification** at the call site (`core/error/SafeErrorMapper`, extended with
`CatchRecordErrorClassification`) and a **`runAttemptCount` cap** read from the `CoroutineWorker`'s own
`runAttemptCount` property (no new persisted counter): once a transient failure has been retried
`MAX_ATTEMPTS` times with no terminal classification, the worker now treats the attempt as terminal
(`SubmissionFailed`) rather than retrying indefinitely. A terminal classification always wins immediately,
regardless of `runAttemptCount`. See the approved plan's §6 error-classification table for the full mapping
and `CatchRecordSyncWorker` (Phase 5) for the implementation.

`MAX_ATTEMPTS = 8` is a judgment call (no backend/user-specified number exists): generous enough to absorb a
flaky mobile connection across WorkManager's exponential backoff, bounded enough not to leave a draft
silently retrying for days. It should be revisited once real-world sync telemetry exists.

### 401 handling: one retry via the existing silent token refresh, not a new MSAL call

The plan asks for "one `acquireTokenSilent` attempt" before giving up on a `401`. Rather than plumb
`NativeAuthClient` into the worker and view model, both simply **retry the whole `submit()` call once**:
`AuthInterceptor` already calls `tokenProvider.currentToken()` on every HTTP request, which performs a
normal MSAL silent-flow lookup (auto-refreshing via the refresh token if the access token has expired). A
retried call therefore gets a fresh silent-refresh attempt for free. If the retry still fails
authentication, the draft becomes terminal `SubmissionFailed(NeedsSignIn)`. This avoids a second,
worker/view-model-specific path into MSAL for the same effect.

### Phase 4: double-submit prevention and legacy non-`GBR-` references

The online (view-model) and background (worker) paths must never run the create→replace→submit sequence
concurrently for the same draft. `RealCatchRecordSubmissionRepository` holds a per-draft
`ConcurrentHashMap<String, Mutex>`, acquired for the whole sequence: an in-process concurrent call for the
same `draft.id` suspends until the first finishes, then observes the already-advanced `submissionStep` and
short-circuits the steps already done. This is process-local only (both callers run in the same app
process), which is sufficient here — there is no multi-process writer to this repository.

Legacy references created under `MIGRATION_9_10`'s `-2`/`-3` collision-suffix scheme do not match
`^GBR-[A-Z0-9]+-\d{6}-\d{6}$` and were deliberately left untouched by the v11→v12 rewrite (R-4): the
repository regenerates and writes back a fresh `GBR-…` reference for any draft whose reference fails this
pattern check, **before** the first `POST /v1/catch-records` call of its sequence.
