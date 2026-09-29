# 0003 - Room for offline-first persistence

## Status

Accepted

## Context

The app must work fully offline (recording catches at sea with no connectivity) and sync data once back
online. This requires reliable local structured storage with migration support, plus a background sync
mechanism.

## Decision

Use **Room** (`androidx.room`) as the local persistence layer, paired with **WorkManager** for deferred
background sync (to be introduced when real sync logic is implemented in a later stage). Stage 1 only adds
the Room Gradle plugin, KSP annotation processing and a schema-export directory (`app/schemas`) as a
compile-time stub — no real entities, DAOs or migrations are introduced yet.

## Update (2026-09-11)

The "no real entities yet" statement above is now superseded for the catch-record draft feature: **Room is
real** for that feature as of [ADR 0006](0006-catch-record-draft-persistence-and-encryption.md), which also
adds SQLCipher-based at-rest encryption of the database file. This ADR's choice of Room as the persistence
layer stands unchanged; only the "stub" scope note is updated. Other features may still be Room stubs until
their own persistence needs are designed.

## Consequences

- Room's compile-time verified SQL and schema export give safer migrations than a hand-rolled SQLite
  layer.
- KSP (not kapt) is used for the Room annotation processor, per current Google guidance and faster build
  times.
- Conflict resolution for sync (last-write-wins vs. merge) is deferred to the stage that introduces real
  sync logic, and must be designed deliberately per the offline-first / error-handling standards (never
  silently drop conflicting data).
