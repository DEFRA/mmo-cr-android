# 0006 - Catch-record draft persistence & at-rest encryption

## Status

Accepted

## Context

[ADR 0003](0003-room-offline-persistence.md) introduced Room only as a compile-time stub ("no real
entities yet"). The "Create a Catch Record" journey (Epic E4-S01) now needs a **real, offline-first,
multi-step draft aggregate** (vessel, trip dates, ports, gear uses, statistical-rectangle selections,
species weights, landing/storage entries) that survives process death and app restarts while the vessel is
at sea with no connectivity, per copilot-instructions §2 offline-first and security constraints.

The catch-record draft is operational fishing data that must be protected at rest on a device that may be
lost, stolen, or shared. This requires an at-rest encryption story that is Secure by Design (OWASP MASVS),
not just Room's default plaintext SQLite file.

## Decision

Persist the catch-record draft aggregate as **real Room entities/DAOs**, with the underlying SQLite
database encrypted using **SQLCipher for Android** (`net.zetetic:sqlcipher-android`), wired into Room via
`net.zetetic.database.sqlcipher.SupportOpenHelperFactory` (the current `sqlcipher-android` 4.x
`androidx.sqlite.db.SupportSQLiteOpenHelper.Factory` implementation) as Room's `openHelperFactory`.

- The SQLCipher passphrase is a randomly generated value, **never hard-coded**, generated once per install
  and **wrapped (encrypted) by a non-auth-bound Android Keystore key** (`AndroidKeyStore` provider, AES-GCM,
  hardware-backed where the device supports it). The wrapped passphrase is stored in **internal app
  storage only** (`filesDir`, `MODE_PRIVATE`), never external storage, never a backup-eligible location,
  and excluded from Android Auto Backup / cloud backup per the security instructions.
- The Keystore key is **not** biometric/auth-bound: the database must be readable in the background (e.g.
  by WorkManager sync work in a later stage) without requiring the user to re-authenticate every time. App
  re-entry biometric gating (already covered by `core/security`) is a separate, UI-level concern from
  database-file-at-rest encryption.
- We explicitly **reject `androidx.security-crypto`** (Jetpack Security Crypto — `EncryptedFile` /
  `EncryptedSharedPreferences`) for this purpose: it is in maintenance mode / deprecated upstream with no
  Kotlin-first replacement announced at time of writing, and per the repo's security instructions,
  deprecated crypto libraries must not be adopted for new work. It remains unused anywhere in this project.
- Foreign keys cascade (`onDelete = CASCADE`) from the draft root to its child tables, supporting FR10's
  dependent-data invalidation (e.g. changing a stat-rectangle selection invalidates dependent species/weight
  rows) without orphaned rows.
- **One active (non-terminal-status) draft per vessel** (the confirmed "draft singularity" assumption) is
  enforced at the repository layer, inside the same transaction that looks up/creates a draft (Room's
  annotation-based indices cannot express a partial/conditional `WHERE status IN (...)` unique constraint);
  a plain `(vesselId, status)` index supports the lookup query's performance.
- Room's schema export (`app/schemas`, already enabled by ADR 0003) now has real schema JSON committed for
  the catch-record entities, enabling safe future migrations.

This ADR supersedes the "no real entities yet" statement in ADR 0003: Room is no longer a stub for this
feature — ADR 0003's choice of Room as the persistence layer stands, but its schema is now real.

## Consequences

- A new build dependency, `net.zetetic:sqlcipher-android`, is added (pinned version in the Gradle version
  catalog) alongside `androidx.sqlite` — this is a deviation from the plain Room defaults noted in
  copilot-instructions §5 and is recorded here as the governing ADR.
- Every read/write to the catch-record Room database now pays a small SQLCipher overhead (page-level AES);
  this is acceptable for the draft's data volume (single active draft per vessel, low write frequency).
- Losing the wrapped-passphrase file (e.g. app data cleared) makes the encrypted database permanently
  unreadable; this is an accepted trade-off consistent with "app data cleared" already discarding local
  state under Android's app-storage model. The repository layer must treat an `SQLiteException` on open as
  a **terminal, non-retryable** error distinct from a transient offline failure (see error-handling
  standards) and must not attempt to silently delete/recreate user data as its first recovery step.
- Unit/instrumented tests must include an encryption smoke test asserting the raw database file is **not**
  plaintext-readable (e.g. a known field value does not appear as a substring of the raw file bytes).
- `androidx.security-crypto` remains listed in the Gradle version catalog only if another Stage-1 area
  still references it; this feature does not depend on it and no new code may introduce a dependency on it.
