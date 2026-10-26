# 0016 - Reference-data API integration (supersedes 0008)

## Status

Accepted — supersedes [0008](0008-reference-data-stub.md)

## Context

A real reference-data service now exists and is reachable from this machine over VPN:
[`mmo-cr-reference-data-service`](https://github.com/DEFRA/mmo-cr-reference-data-service), whose
[`docs/api-reference.md`](https://github.com/DEFRA/mmo-cr-reference-data-service/blob/main/docs/api-reference.md)
is the authoritative contract for this ADR (no OpenAPI spec is published). This ADR covers only the
**networking foundation** (Phase 1): HTTP client wiring, environment flavors, auth/cleartext handling, and
the integration strategy the later DTO/Room/repository phases will build on. It does not itself add
dataset DTOs, Room caching, or swap `ReferenceDataRepository`'s binding away from ADR 0008's stub — that
remains bound until a later phase.

Key facts verified directly against the DEV environment and the committed Docker-local stack
(`DEFRA/mmo-cr-backend-local`), not assumed from the contract alone:

- DEV gateway base: `https://mmo-cr-catch-recording-service.api.dev.cdp-int.defra.cloud/reference-data-service/`,
  with API routes under `api/v1/reference-data/`. `/health` sits at the **service root**, not under the
  API path prefix.
- DEV's auth stub accepts **any non-empty bearer token** (`Authorization: Bearer local-stub` → 200;
  no header → 401). This is a stub, not real authentication — see "Interim defaults" below.
- The DEV TLS chain terminates at the publicly-trusted Amazon Root CA 1, so the existing `main` network
  security config (system trust anchors, no cleartext) needs no change for DEV/prod traffic.
- The local Docker stack serves the reference-data-service on host port 3002, plain HTTP, no gateway
  prefix; from an emulator this is `http://10.0.2.2:3002/`.

## Decision

### Contract usage: `view=mobile`, manifest, conditional requests

Later phases will request the `?view=mobile` projection documented in api-reference.md's "Canonical and
mobile views" section for every dataset (vessels, gears, ports, species) — it already strips
internal-only fields and keeps the GUID + primary business code the app needs. The **manifest**
(`GET manifest`) is the source of a dataset's current `version`/`etag`/`url` and is used to decide whether
a cached dataset needs refreshing, before fetching the dataset itself.

Caching uses **two distinct ETag concepts**, per api-reference.md's "Conditional requests and caching"
section — they are deliberately not the same value and must not be conflated:

1. The **manifest's own `version`/`etag` per dataset** — checked first, cheaply, to decide "has this
   dataset changed since I last cached it" without fetching the (potentially large) collection.
2. The **per-request collection/item `ETag` response header** — stored alongside a cached response and
   sent back as `If-None-Match` on the next fetch of that exact request (same dataset + query params), to
   get a cheap `304 Not Modified` instead of re-downloading an unchanged body.

Offline precedence for every dataset read (fully designed in a later phase; recorded here for
continuity): **fresh network response → Room cache (still within its freshness window) → stale Room
cache (network unavailable) → bundled stub fallback** (ADR 0008's data, narrowed rather than deleted —
see "Out of scope" below) only if no cache has ever been populated.

### Product flavors: `local` / `dev` / `prod`

A new `environment` flavor dimension adds `local`, `dev`, `prod` flavors, each supplying a
`REFERENCE_DATA_SERVICE_URL` `BuildConfig` field (the **service root**, e.g. including no `api/v1/...`
suffix) so `/health` and the API base can both be derived correctly:

| Flavor | `REFERENCE_DATA_SERVICE_URL`                                                              |
| ------ | ------------------------------------------------------------------------------------------ |
| local  | `http://10.0.2.2:3002/`                                                                    |
| dev    | `https://mmo-cr-catch-recording-service.api.dev.cdp-int.defra.cloud/reference-data-service/` |
| prod   | `""` (deliberately empty)                                                                  |

`prod`'s empty URL is intentional: the network module fails fast with a clear, typed exception at
first use rather than silently defaulting to DEV. A developer may override the service root locally via
a gitignored `local.properties` key (`referenceData.serviceUrl`), read in `app/build.gradle.kts` and
exposed as the same `BuildConfig` field, without ever being committed. No `applicationIdSuffix` or
signing changes are made — flavor-aware signing/Play-track handling is DevOps-owned and only recommended
here, not implemented.

### Token abstraction

A `TokenProvider` interface (`suspend fun currentToken(): String?`) is bound per flavor rather than
hardcoded into the interceptor, so swapping in real auth later is a DI-only change:

- `local`/`dev` bind **`StubTokenProvider`**, returning a fixed non-secret literal (e.g. `"local-stub"`),
  matching DEV's always-allow auth stub.
- `prod` binds **`MissingTokenProvider`**, returning `null` — there is no real token source yet, and
  `prod`'s empty service URL already fails fast before any request would be attempted.

Tokens are held in memory only; `androidx.security:security-crypto` is **not** used for this (already
rejected app-wide per ADR 0006, as it is deprecated upstream).

`AuthInterceptor` and `AcceptLanguageInterceptor` each wrap their lookup in a short `runBlocking` with a
2-second timeout, degrading to "no header" on any failure or timeout. This is safe because OkHttp never
runs an interceptor on the main thread — it runs on the calling thread for a blocking `Call.execute()`, or
on OkHttp's own dispatcher pool for an async/Retrofit-suspend call — so this does not violate the "no
blocking `runBlocking` on the main thread" rule from the security instructions.

### Debug-only scoped cleartext for the emulator loopback

`main`'s `network_security_config.xml` is **unchanged** (`cleartextTrafficPermitted="false"` app-wide,
system trust anchors) and is what every release build ships. A new **debug-only**
`app/src/debug/res/xml/network_security_config.xml` adds a `domain-config` permitting cleartext **only**
for `10.0.2.2` and `localhost` (`includeSubdomains="false"`), with the same `cleartextTrafficPermitted
="false"` base-config as `main` for everything else. `<debug-overrides>` was considered and rejected — it
has no `cleartextTrafficPermitted` attribute, so it cannot express a scoped cleartext exception; a
debug-variant resource override is the correct mechanism here.

### No certificate pinning (for now)

Certificate pinning is not added in this phase. Rationale: DEV's chain terminates at a publicly-trusted,
ACM-auto-rotated root (Amazon Root CA 1), the environment is VPN-only (not public internet-facing), and
the app has no existing pinning precedent to extend. This should be revisited with DEFRA Cloud Mobile
Services before a production rollout (see "Interim defaults", Q9).

## Interim defaults / open questions

Recorded here so each is visible and revisitable, per the approved brief, rather than silently decided:

- **Q2 (real token)** — `local`/`dev` use `StubTokenProvider`; `prod` uses `MissingTokenProvider`
  (returns `null`, so no `Authorization` header is sent) until real auth is designed.
- **Q3 (port → statistical-area mapping)** — `PortStatisticalAreaLookup` (Phase 2, fixed Phase 4/5 review)
  is keyed by port `code`. Only the two live DEV codes matching the confirmed stub departure ports are
  populated so far (`GBHTG` → `AREA-HASTINGS`, `GBDVR` → `AREA-DOVER`, the same area ids the stub's
  statistical sub-rectangles use); every other port code sentinel-falls-back to `"AREA-UNASSIGNED"`, which
  `MapSupport.nearbyRectanglesFor` already handles as an empty (not crashing) result. Full port coverage is
  still a decision for where this mapping will ultimately come from.
- **Q4/Q5 (sub-rectangles, previously-used ports)** — remain on the stub fallback for now.
- **Q6 (species quota/precision)** — keeps the app's existing domain defaults.
- **Q7 (gear measurement keys)** — finalized translation table (Phase 2), in
  `ReferenceDataMappers.gearMeasurementKeyFor`:

  | API `code` | Persisted key                 | Unit   | Derived type |
  | ---------- | ------------------------------ | ------ | ------------- |
  | `ME`       | `mesh_size_mm` (reused)         | `mm`   | Decimal       |
  | `GM`       | `api_gear_measurement_gm`      | `m`    | Decimal       |
  | `GN`       | `api_gear_measurement_gn`      | count  | Integer       |
  | `MT`       | `api_gear_measurement_mt`      | count  | Integer       |
  | `QG`       | `api_gear_measurement_qg`      | count  | Integer       |
  | `HE`       | `api_gear_measurement_he`      | `m`    | Decimal       |
  | `NL`       | `api_gear_measurement_nl`      | `m`    | Decimal       |
  | `NN`       | `api_gear_measurement_nn`      | count  | Integer       |
  | `NI`       | `api_gear_measurement_ni`      | count  | Integer       |

  Only `ME` has a reliable semantic match to a pre-existing stub key (`mesh_size_mm`); the other 8 live
  codes do not correspond 1:1 to the 8 remaining stub keys (e.g. `number_of_trawl_nets`,
  `total_hooks_hauled`), so guessing a reuse risked silently mismapping a captured value. Each gets its
  own new, deterministic, code-derived key (`api_gear_measurement_<lowercase code>`); any future unseen
  code falls back to the same deterministic pattern. The persisted key *space* is additive — the 8 unused
  stub keys are left intact for the stub repository until it is retired (Phase 6). `kind` is always
  `"number"` in the live API, so type is derived from `unit` instead: `count` → Integer; `m`/`mm` →
  Decimal; any other/missing unit also defaults to Decimal.
- **Vessel length bands** — `gears?vesselLengthMetres=` narrows which measurements are returned
  (under-10m → `ME`,`GN` only; 10-to-12m and over-12m → all nine). The default approach is to cache one
  `gears` response per documented band using a representative length, and select by the vessel's
  `lengthOverallMetres`. Preferred long-term fix (asked of the backend team): expose the bands per
  measurement directly, removing the need to request per-band.
- **Q9 (production security profile)** — DEFRA Cloud Mobile Services must be consulted on a security
  profile before production rollout, per the DEFRA mobile standard for non-public DEFRA systems; this is
  explicitly not resolved by this ADR.

## Update (Phases 2–3): DTOs, mapping, error handling, Room cache

Phase 2 adds `internal` DTOs (`feature/catchrecord/data/referencedata/remote/`), a `ReferenceDataApi`
(`Response<T>`-wrapped so a bodyless `304` is observable), mappers to the **unchanged** domain models in
`domain/referencedata/ReferenceDataModels.kt`, and a `schemaVersion` guard (supported `"1.0"`; a mismatch
throws a dedicated, never-cached, Terminal exception). `SafeErrorMapper` gained a `ReferenceDataApiException`
branch honouring the server's `retryable` flag when present, defaulting by HTTP status otherwise (5xx
transient, 4xx terminal; 401 is its own type for a future re-auth flow). A malformed error body falls back
to the same status-based default rather than failing the whole classification.

**Domain-model pressure (none acted on):** several live API fields have no home in the existing domain
models and are deliberately kept DTO/entity-only rather than growing the models: vessel `pln`/`cfr`/
`lengthOverallMetres`, port `coordinate`, species `scientificName`, gear `category`/`pairFishing`. None of
these are currently needed by any screen; adding them was judged out of this phase's scope and is flagged
for a product decision rather than silently added.

**Update (Trip ID):** vessel `pln` is now carried into the domain `Vessel` as the interim prefix of the
catch-record Trip ID `RSS_NUMBER-DDMMYYYY-HHMMSS` (UK local time), generated once when a record is started
(`CatchRecordReferenceGenerator`). The true RSS number (`identifiers.registrationNumber`) exists only in the
canonical vessel view, not the mobile view; swap the prefix source if the mobile projection gains it.
Uniqueness: Room DB v10 adds a unique index on `catchRecordReference` (the migration de-duplicates existing rows
with `-2`, `-3` suffixes). On a clash, the DAO's find-or-create transaction moves the time on by 1 second, so the
Trip ID can be a few seconds after `createdAt`. Starting a record is blocked when the vessel has no PLN.
Cross-device uniqueness still needs a backend unique constraint and 409 handling (follow-up).

**Species language-cache strategy:** `ReferenceDataMetadataEntity.languageTag` stores the `Accept-Language`
tag the cached `species` rows were resolved under. The *storage* exists now; the invalidate-on-change
*enforcement* (compare the ADR-0012 current language against the stored tag before serving/trusting the
cache) is deferred to the Phase 4/5 repository, which owns cache-freshness decisions generally.

Phase 3 adds Room entities for the four cached datasets plus `ReferenceDataMetadataEntity`, a
`ReferenceDataDao` with transactional per-dataset `replaceAll`, and `CatchRecordDatabase` v7 → v8 via a real
additive `Migration(7, 8)` (ADR 0010 bans `fallbackToDestructiveMigration`). Gears/measurements are keyed by
`(id, vesselLengthBand)` since the same gear legitimately recurs across bands with different measurement
sets. Neither phase wires into `ReferenceDataRepository`'s binding — that remains ADR 0008's stub until a
later phase.

## Update (Phase 4): manifest/ETag-driven background refresh

`ReferenceDataRefresher` fetches the manifest once per run, then refreshes vessels/ports/species/gears
**independently** (each wrapped so one dataset failing never discards another's successful update): a
dataset is skipped entirely (no network call) if its manifest `version`/`etag` is unchanged, otherwise it
is fetched with the stored response `ETag` as `If-None-Match` (byte-for-byte, including quotes); a `304`
only touches `fetchedAtEpochMillis`; a `200` replaces that dataset's rows and stores the new manifest
version/etag, response ETag, and (species only) the `Accept-Language` tag used. A species language change
forces a refetch **without** `If-None-Match` even if the manifest is unchanged, closing the Phase 3
enforcement gap noted above. A `schemaVersion` mismatch throws and is never cached, per dataset.

**Gears are cached as four variants** sharing the manifest's single `gears` entry but each with its own
metadata row/ETag: `under-10m` (5.0m), `10-to-12m` (11.0m), `over-12m` (15.0m), and an unfiltered `all`
(no `vesselLengthMetres` sent) for when a vessel's length is unknown. A manifest change therefore
refetches all four bands independently (each may individually `304` or `200`).

`ReferenceDataSyncWorker` (`@HiltWorker CoroutineWorker`) maps the refresh outcome to `Result.success()`
(no failures), `Result.retry()` (any transient failure, via `SafeErrorMapper`), or `Result.failure()`
(only terminal failures, e.g. 401/403/schema-mismatch — never retried in a loop).
`WorkManagerReferenceDataSyncScheduler` enqueues a `CONNECTED`-constrained unique periodic refresh every
**12 hours** (`KEEP` policy, 15-minute exponential backoff) plus a one-time refresh at app start
(`REPLACE` policy), mirroring `CatchRecordSyncWorker`/`WorkManagerCatchRecordSyncScheduler`. Twelve hours
was chosen over the server's `stale-while-revalidate=86400` (24h) because reference data is read on every
wizard step and a same-day backend correction (e.g. a port renamed) should reach users without waiting a
full day, while still being far looser than the submission sync worker's cadence since this data changes
rarely.

**Deviation flagged for review:** `ReferenceDataRemoteDataSource.fetchVessels`/`fetchPorts`/`fetchSpecies`
(Phase 2) returned domain-mapped lists; `fetchGears` returned `List<GearType>`. Phase 4's entity mapper
needs DTO fields the domain mapper discards (`pln`/`cfr`/`lengthOverallMetres`, port `coordinate`, gear
`measurements`), so these four methods were changed to return the raw DTOs (`GearsFetchPayload` for
gears) instead; DTO→domain mapping moves to the Phase 5 repository. No production code depended on the
old signatures (only the Phase 2 test, updated to match).

## Update (Phase 5): repository composition and the DI swap

`CachingReferenceDataRepository` now backs the `ReferenceDataRepository` binding. Reads never block on
the network when a cache exists: a cache hit returns immediately, and if older than a **1-hour** freshness
TTL it enqueues `WorkManagerReferenceDataSyncScheduler`'s existing background refresh rather than fetching
inline (a much tighter window than Phase 4's 12-hour worker period, since a stale-but-present cache is
still served instantly — the TTL only decides when to *also* kick a refresh, never whether to block). An
empty cache that is online makes one bounded (5s) inline fetch-and-cache using the Phase 2 DTO→domain
mappers directly (no redundant Room round-trip); a timeout, any fetch failure, or being offline all fall
back to ADR 0008's stub with no hang — connectivity is checked before attempting a fetch, so an offline
empty cache never touches the network. Previously-used ports and statistical sub-rectangles have no live
equivalent and pass straight through to the stub, per "Out of scope" below.

**Vessel-length gear bands.** `Vessel` gained an optional `lengthOverallMetres`; `getGearTypes` gained an
optional `vesselLengthMetres` parameter, defaulted so existing callers and the stub still compile. The
gear-selection view model (`CatchRecordFlowViewModel.vesselSelected`) resolves the selected vessel's
length from already-loaded state and passes it through; a null length (vessel not found, or no length on
record) selects the Phase 4 `all` cache variant.

**401/403.** A cache hit never calls the network directly, so an expired/invalid stub token cannot wipe a
served cache. A background refresh's own 401/403 handling (terminal, no retry loop) is unchanged from
Phase 4.

**Stub-ID draft resume.** A draft saved before this change references stub ids (e.g. `port-hastings`) that
will not exist in live reference data. `DraftInvalidation.reconcileAgainstLiveGearTypes` already clears a
gear use's dependent answers when its `gearTypeId` is not among the live gear types; `CatchRecordFlowView
Model.enterFlow` now runs this reconciliation against whatever `getGearTypes()` currently returns before
showing the draft. **What the user sees:** the affected gear's "Used on trip?" question reverts to
unanswered and its statistical rectangle/species entries are cleared — nothing crashes, and every other
part of the draft (vessel, trip dates, other gear uses) is unaffected. The user simply re-answers that one
question.

**Deferred stale-data UI.** The stub fallback guarantees every read returns *some* data, so this phase adds
no "cache is stale" or "showing offline data" indicator. This is a deliberate, recorded deferral, not an
oversight — revisit once there is a concrete UX need (e.g. a support ticket about silently stale prices).

## Update (Phase 6): stub-tier ID fix, fallback framing, and close-out

**Stub-tier ID-format fix.** The live DI swap (Phase 5) exposed two `CachingReferenceDataRepository`
methods still passing a live GUID straight to the stub, which only recognises its own stub ids:

- `getStatisticalSubRectanglesForPort(portId)` now resolves `portId` against the cached/live ports
  first (falling back to the stub's own port list only when the cache is empty), then filters the
  stub's sub-rectangles by that port's `statisticalAreaId` — the same pattern `MapSupport` already
  uses. An unresolved port id now succeeds with an empty list rather than failing: the interface KDoc
  does not mandate a failure, and empty-on-unknown matches the map screen's own fallback. The stub's
  own implementation was aligned to the same empty-on-unknown contract for consistency.
- `getPreviouslyUsedPorts(vesselId)` needed no code change: the stub's vessel-keyed map simply has no
  entry for a live vessel GUID, so it already returned success with an empty list. Confirmed (with a
  new Compose test) that `PortSelectionStepContent` — shared by the departure and return port screens —
  already goes straight to the search/autocomplete field with no empty "favourites" heading when the
  list is empty, so no screen change was required.

**Fallback framing — kept the class, renamed the role.** `StubReferenceDataRepository` is referenced
by name across 13 files (the DI module, KDoc cross-references in two gear screens, and unit/instrumented
tests). Renaming the class would touch all of them for a documentation-only change, so the class keeps
its name; its KDoc now opens by stating its narrowed role explicitly — a fallback composed as the
`stub` tier behind `CachingReferenceDataRepository` with two remaining jobs:

1. the empty-cache **offline-cold-start tier** for vessels/ports/species/gears (so a cold start with no
   cache and no network still works);
2. the **sole source** for what the live API does not yet provide: statistical sub-rectangles,
   port → statistical-area mapping, previously-used ports, and species quota/precision.

Every placeholder/TBC row inside it was already clearly marked (per ADR 0008's consequence clause) in
earlier phases and remains unchanged — no data was deleted or renamed.

**Previously-used-ports follow-up (recorded, not built).** There is no API for this (Q5). The proper
long-term source is almost certainly the user's own local draft/submission history, not a reference-data
endpoint — that is out of scope for this ADR and is tracked as a follow-up: *derive previously-used ports
from local draft history*.

## Open questions — final status

| # | Topic | Status |
| - | ----- | ------ |
| Q1 | DEV reachability/seeding | **Resolved** — seeded 2026-10-09; confirmed end-to-end including an on-device smoke test against DEV |
| Q2 | Real token issuer | **Open** — `StubTokenProvider`/`MissingTokenProvider` remain interim |
| Q3 | Port → statistical-area mapping | **Open** — only `GBHTG`/`GBDVR` populated in `PortStatisticalAreaLookup`; everything else sentinel-falls-back |
| Q4 | Sub-rectangles from the API | **Open** — no API source; stub remains the sole source, now correctly resolved against live port ids (Phase 6 fix) |
| Q5 | Previously-used ports from the API | **Open** — no API source; follow-up recorded above (derive from local draft history) |
| Q6 | Species quota/precision | **Open** — app's existing domain defaults retained |
| Q7 | Gear measurement key translation table | **Accepted** — only `ME` is a semantic match; the stub's other keys record catch activity (pots hauled/left in water) while the API's describe gear characteristics, which is a product question, not a mapping bug |
| — | Vessel-length bands | Option (a) implemented (four cached variants); option (b) — exposing bands per measurement directly — requested of the backend team |
| Q9 | Production security profile | **Open** — DEFRA Cloud Mobile Services must be consulted before production (no certificate pinning yet) |
| Q10 | `applicationIdSuffix` per flavor | **Open** — DevOps-owned; recommended only, not implemented |
| Q11 | CI flavor matrix | **Open** — DevOps-owned; Phase 1's Fastfile/CI changes are an interim single-flavor pipeline |
| — | Previously-used-ports follow-up | **Open** — derive from local draft/submission history |

## Out of scope (this ADR)

- `/map/*` API datasets — ADR 0013's build-time GeoJSON pipeline remains authoritative; see that ADR's
  appended scope note.
- Real authentication, catch-record submission, and new loading/stale-data UI (see the deferral above).

## Consequences

- `docs/adr/0008-reference-data-stub.md`'s Status is updated to "Superseded by 0016"; its stub
  implementation is retained (narrowed, not deleted) as the final offline fallback tier, now resolving
  sub-rectangles against live port ids (Phase 6) rather than only its own stub ids.
- As of Phase 5, `ReferenceDataRepository` is bound to `CachingReferenceDataRepository`: vessels, ports,
  species and gears are now live-data-backed (cache-first, stub fallback); previously-used ports and
  sub-rectangles remain stub-only (see "Out of scope"). This is the first user-visible change in this ADR.
- Three product flavors multiply every build/test/lint variant task name (e.g. `testDebugUnitTest` →
  `testLocalDebugUnitTest`/`testDevDebugUnitTest`/`testProdDebugUnitTest`); CI/Fastlane/Sonar changes
  needed to keep the pipeline green are tracked in Phase 1's own report, flagged for DevOps review since
  those files are DevOps-owned.
- This phase (6) is the last planned phase of this ADR's rollout; remaining items are tracked in the
  "Open questions — final status" table above, not as further work under this ADR.
