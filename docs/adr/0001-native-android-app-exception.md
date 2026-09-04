# 0001 - Native Android app as a governed exception to the DEFRA mobile standard

## Status

Accepted (governed exception — to be logged with Delivery Architecture)

## Context

The DEFRA mobile standard's default guidance is not to build native mobile applications, favouring
web/responsive or cross-platform solutions where possible. The MMO Catch Recording app has offline-first
requirements (recording catches at sea with no connectivity), on-device biometric re-entry, and secure
local data-at-rest needs that are difficult to meet reliably with a web/hybrid approach on the target
vessel hardware.

## Decision

Build the Catch Recording app as a **native Android application** (Kotlin + Jetpack Compose), as a
deliberate, documented exception to the DEFRA mobile standard's default guidance, per
copilot-instructions.md §2 (mandatory DEFRA constraints — exceptions must be explicit and governed).

An equivalent iOS native app is anticipated; iOS-specific ADRs are TBC and will be cross-referenced from
this ADR set once authored (see `0000-ios-adr-references-TBC.md`).

## Consequences

- This decision **must be logged as a governance exception** with Delivery Architecture
  (delivery.architecture@defra.gov.uk) before/alongside release, per the DEFRA standards precedence.
- The team accepts the maintenance cost of a native codebase (and, in future, a parallel iOS codebase)
  instead of a single cross-platform codebase.
- Offline-first, on-device secure storage (Android Keystore) and biometric APIs are directly available,
  simplifying Stage 2+ security work.
- Native distribution is via Google Play (internal/closed/production tracks) — release engineering owned
  by a separate DevOps role/agent, out of scope for this ADR.
