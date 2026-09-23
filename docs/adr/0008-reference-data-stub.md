# 0008 - Reference data is a bundled local stub

## Status

Accepted

## Context

The catch-record wizard needs reference data — vessels, ports, gear types, species, statistical
sub-rectangles, and a port→statistical-area mapping — to drive selection screens (vessel picker, port
pickers, gear/species pickers). No real MMO reference-data API is available or confirmed at this stage; only
a handful of Stage-1 screenshot examples exist (vessels ACHILLES/HERCULES; ports Hastings/Dover).

Blocking this feature on a real reference-data API would stall the whole wizard build. Later phases also
need enough placeholder gear/species/stat-area data to write meaningful unit tests before real data exists.

## Decision

Introduce a `ReferenceDataRepository` interface in the domain layer, with a **bundled, local stub
implementation** (in-code dataset, structured the same way a bundled JSON asset would be) providing:
vessels, ports, gear types, species, statistical sub-rectangles, and the port→statistical-area mapping.

- The interface is deliberately shaped so a future Retrofit/network-backed implementation (or a
  Room-cached, WorkManager-refreshed one) can replace the stub **without changing any domain or
  presentation code** that depends on `ReferenceDataRepository` — callers only see suspend
  functions/`Flow`s returning domain value types, never a network/DTO shape.
- The stub includes the confirmed Stage-1 data points (vessels ACHILLES/HERCULES; ports Hastings/Dover)
  plus enough placeholder gear/species/stat-area rows to unblock later phases' unit tests (gear-loop,
  species/weight entry, stat-rectangle selection) before real screenshots/data are available.
- Following the existing Stage-1 convention (`Fake*Repository` classes bound in `di/RepositoryModule.kt`),
  the stub is bound as the sole implementation for now; swapping it later is a one-line DI change.

## Consequences

- Reference data is **read-only and static** for this phase — there is no sync/refresh logic yet, and no
  network calls are made. This is explicitly out of scope per the story brief (no network/submission/sync
  code).
- Any hard-coded reference data used only to unblock tests (e.g. filler gear/species/stat-area rows beyond
  the confirmed screenshots) must be clearly marked as placeholder/TBC in code comments so it is not
  mistaken for real MMO reference data when the feature moves to a real API.
- When a real reference-data API is introduced, this ADR should be revisited/superseded rather than
  silently replaced, so the swap is visible in governance history.

## Update (2026-09-18)

This drop-in-replaceable stub pattern now also has a sibling for map geometry: the statistical sub-area map
feature ([ADR 0013](0013-offline-statistical-area-map.md)) introduces a dedicated `MapGeometryRepository`
interface, backed by `AssetMapGeometryRepository` — a **bundled, build-time-precomputed binary asset stub**,
following exactly this ADR's principle (interface-first, callers never see the underlying data-source shape,
swap is a one-line DI change). It is a **separate repository/interface** from `ReferenceDataRepository` (the
existing `StatisticalSubRectangle` domain model — id/code/statisticalAreaId — is unchanged), not a
replacement for it; the two repositories serve different concerns (reference lookup data vs. renderable
geometry) but share the same "drop-in-replaceable stub, no network yet" governance posture.

