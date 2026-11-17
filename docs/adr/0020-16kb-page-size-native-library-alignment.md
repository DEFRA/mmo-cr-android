# 0020 - 16 KB page-size alignment for bundled native libraries (SQLCipher)

## Status

Accepted (2026-11-12). Verified compliant; no change needed.

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

**No change needed: every native library in the release build is already 16 KB-aligned.** Verified
2026-11-12 on the compiled release base bundle (`v2.0.0-BUILD_19`): a universal APK built with bundletool
passes `zipalign -c -P 16 -v 4`, and every `PT_LOAD` segment of all 12 libraries has alignment 16384:

| ABI | Libraries |
|---|---|
| `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` | `libsqlcipher.so`, `libdatastore_shared_counter.so`, `libandroidx.graphics.path.so` |

Re-run the same check whenever a dependency that ships native code is added or upgraded (for example
SQLCipher, or a new MSAL transitive). The candidate steps below were the investigation plan:

- Verify the bundled SQLCipher native libraries' page-size alignment (Android Studio APK Analyzer,
  `zipalign`/ELF program header inspection, or Play Console's pre-launch report) against Zetetic's
  published 16 KB support status for the pinned version.
- If unaligned, upgrade `net.zetetic:sqlcipher-android` to a version with confirmed 16 KB-aligned
  native libraries, re-run the Room/SQLCipher test suite, and re-verify.
- If no aligned version is available, raise with Zetetic/DEFRA Delivery Architecture before the next
  Play upload blocked by this requirement.

## Consequences

Resolved: Play uploads are not blocked by 16 KB alignment for the current dependency set. Adding or
upgrading native-code dependencies needs the check above before release (DevOps verifies in the pipeline).
