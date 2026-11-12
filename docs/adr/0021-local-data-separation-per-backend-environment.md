# 0021 - Separating local data storage per backend environment across Play tracks

## Status

Proposed

## Context

All product flavors (`local`, `dev`, `prod` — ADR 0015/0016) share a single `applicationId`
(`mmo.catchrecording.android`, `app/build.gradle.kts`), each pointed at a different
`REFERENCE_DATA_SERVICE_URL` backend. A device enrolled in more than one Play testing track (e.g.
internal and closed testing) can receive builds built from different flavors/backends under this
same package, sharing one on-device encrypted Room database (ADR 0006). Updating from one track's
build to another's would reuse the existing draft/catch-record data against a different backend's
reference data (species/area codes, etc.), risking stale or mismatched sync behaviour. This was
raised by the release-engineering lead while building the Play release pipeline.

## Decision

To be investigated and decided. Candidate options:

- Scope local storage (Room database file name/location, and any cached reference data) per backend
  environment, so switching tracks/backends does not reuse another environment's data.
- Treat multi-track installs on one device as unsupported, and document/enforce single-track testing
  as a process control instead of a code change.
- Some combination of the two, informed by how DEFRA's existing Play testing process actually
  enrolls testers.

## Consequences

Unresolved until the above investigation completes and a decision is approved; any code change here
alters persistence architecture and requires updating ADR 0006 and the usual plan-approval gate
before implementation.
