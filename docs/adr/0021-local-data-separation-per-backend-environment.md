# 0021 - Separating local data storage per backend environment across Play tracks

## Status

Accepted (2026-11-13): **process control, no code change**, decided in
[ADR 0022](0022-android-three-app-release-topology.md) (*Switching tracks on one device*). A technical
guard is deferred.

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

**Option 2: treat multi-track installs on one device as unsupported and enforce it as process.** Since
this was raised, the release design changed: there are now three Play apps (Dev, Test, Prod; ADR 0022)
and no product flavors (ADR 0024). Two apps still serve two backends each under one package: the Prod
app (`uat` on Ext-Test; `prep-prod`/`production` on Prod) and the Test app (`test`; `perf-test`).

- Switching a device between tracks with different backends **must uninstall the app first**, which
  removes all its data.
- Each tester is on **one track per app**. This is what stops Play silently offering the higher-versionCode
  internal build as an in-place update.

A technical guard (record the backend id, refuse to show or submit data made for another backend) was
considered and deferred. Revisit if the process is broken in practice or an audit asks for a technical
control. The options originally listed were:

- Scope local storage (Room database file name/location, and any cached reference data) per backend
  environment, so switching tracks/backends does not reuse another environment's data.
- Treat multi-track installs on one device as unsupported, and document/enforce single-track testing
  as a process control instead of a code change.
- Some combination of the two, informed by how DEFRA's existing Play testing process actually
  enrolls testers.

## Consequences

No persistence change, so ADR 0006 is unchanged. The rule is in ADR 0022, the README release section and
the release setup guide. Residual risk if it is broken: one backend's drafts reaching another backend.
