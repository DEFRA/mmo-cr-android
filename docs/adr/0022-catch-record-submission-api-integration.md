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

This ADR covers the full integration design (the approved plan's Phases 1-7); only Phases 0-2 — the ADR
itself, the Room v11→v12 migration and reference-format switch, and the network layer (API/DTOs/exceptions/
DI, not yet wired into app behaviour) — are implemented alongside it. Phases 3 (payload mapper) and 4-7
(repository, worker/status, UI, Hilt wiring) follow once the blocking backend questions below are answered.

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

## Open backend questions (blocking Phase 3 unless noted)

| # | Question | Default taken now |
|---|---|---|
| OQ-2 (**blocking**) | `gears[].statisticalArea.id` is a reference-data row id; the app persists only the sub-rectangle **code**. Resolve via cached reference data once confirmed — do not guess. | Phase 3 stays gated |
| OQ-3 | `characteristics[].characteristicId` (UUID) is currently dropped at `ReferenceDataMappers.kt:73-79`. | Carry `GearMeasurementDto.id` through (no backend dependency) |
| OQ-4 | No RSS available to mobile, only PLN. | Send `GBR-{PLN}-…` (see above); raised with backend |
| OQ-5 (**blocking**) | `NotLandedSpeciesEntry` holds one weight; unclear which server field it maps to. | Default `weightAboveMinimumKg`; Phase 3 stays gated |
| OQ-6 (**blocking**) | `trip.dateStarted`/`dateEnded` format and required-ness are undocumented. | Default `YYYY-MM-DD`, sent only when `startedAndFinishedToday=false`; Phase 3 stays gated |
| OQ-7 | `gears[].associationId` is required by the canonical schema but absent from the backend's own example. | Omit; resend `GearUse.id` if the server 422s |
| OQ-9 | Idempotency-Key replay-window retention is undocumented. | Client assumes it may lapse; relies on the OQ-1 read-back |
| `numberOfShots` | No server field exists for this locally-captured value. | Data is dropped on submission; raised with backend as a possible contract gap |
| RSS vs PLN | See OQ-4. | — |
| 409 vs 412 | RFC 9110 §13.1.1/§15.5.13 mandate `412` for a failed precondition (`If-Match`); the server uses `409` for both version conflicts and other state conflicts. | Client accepts `409` as the server's documented behaviour; raised with backend as a contract-conformance item (R-5) |

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
- Phases 3-7 are sequenced behind this ADR; Phase 3 is explicitly gated on OQ-2/OQ-5/OQ-6 landing from the
  backend team.

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
