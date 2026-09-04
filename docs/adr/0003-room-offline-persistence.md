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

## Consequences

- Room's compile-time verified SQL and schema export give safer migrations than a hand-rolled SQLite
  layer.
- KSP (not kapt) is used for the Room annotation processor, per current Google guidance and faster build
  times.
- Conflict resolution for sync (last-write-wins vs. merge) is deferred to the stage that introduces real
  sync logic, and must be designed deliberately per the offline-first / error-handling standards (never
  silently drop conflicting data).
