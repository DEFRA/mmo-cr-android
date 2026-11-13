# 0020 - 16 KB page-size alignment for bundled native libraries (SQLCipher)

## Status

Proposed

## Context

Google Play requires apps targeting Android 15 (API 35) or later to support devices with a 16 KB
memory page size; `targetSdk` is 36 (`app/build.gradle.kts`), so this requirement applies. Any
app-bundled native (`.so`) library that is not 16 KB-aligned can cause a Play Console upload warning
or rejection. The app bundles `net.zetetic:sqlcipher-android` (version `4.19.1`,
`gradle/libs.versions.toml`) for encrypted Room persistence of the catch-record draft (ADR 0006),
which ships prebuilt native libraries whose alignment has not yet been verified against this
requirement. This was raised by the release-engineering lead while building the Play release
pipeline.

## Decision

To be investigated and decided. Candidate verification/remediation steps:

- Verify the bundled SQLCipher native libraries' page-size alignment (Android Studio APK Analyzer,
  `zipalign`/ELF program header inspection, or Play Console's pre-launch report) against Zetetic's
  published 16 KB support status for the pinned version.
- If unaligned, upgrade `net.zetetic:sqlcipher-android` to a version with confirmed 16 KB-aligned
  native libraries, re-run the Room/SQLCipher test suite, and re-verify.
- If no aligned version is available, raise with Zetetic/DEFRA Delivery Architecture before the next
  Play upload blocked by this requirement.

## Consequences

Unresolved until the above investigation completes; tracked as a backlog item owned by the Android
developer, with release-pipeline verification owned by DevOps.
