# 0027 - Self-service password reset via MSAL native authentication (V1)

## Status

Proposed — 2026-11-24 (becomes Accepted once the spike has run). Closes [ADR 0019](0019-entra-external-id-authentication.md) **Q2** ("Is self-service
forgot-password in scope?"). **Pending follow-up:** the DEV-tenant behaviour spike (see
[Pending spike](#pending-spike-gated-follow-up)) has **not** been run; the SDK-result mapping below is built
on documented assumptions and is isolated so it can be corrected without touching any other layer.

## Context

Users who forget their password need a self-service route back into the app: **Sign in → Forgotten password →
email → reset code → new password → "Your password has been reset" → Sign in**. MSAL Android **8.6.0** (pinned,
ADR 0019) supports this through the Native Auth **V1** GA API: `resetPassword(NativeAuthResetPasswordParameters)`
returns `ResetPasswordStartResult.CodeRequired` or a `ResetPasswordError`;
`ResetPasswordCodeRequiredState.submitCode(String)` returns `PasswordRequired` or a `SubmitCodeError`;
`ResetPasswordPasswordRequiredState.submitPassword(CharArray)` returns `Complete` or a
`ResetPasswordSubmitPasswordError`. The `…V2` classes in the AAR are private preview and remain banned
(ADR 0019). `resetPasswordV2` is never called.

Facts established from the 8.6.0 sources that shape the decisions below:

- There is **no "code expired" check** on `SubmitCodeError` — only `isInvalidCode()` and `isBrowserRequired()`
  plus a raw `subError` string. Entra reports `invalid_oob_value` for a wrong code and `expired_token` for an
  expired reset session; nothing reports a code as "already used".
- Entra's own start-reset call returns `user_not_found` to any caller, so account existence is **observable
  by anyone with the public client ID**, regardless of what this app shows.
- The SDK step objects are `Parcelable` and carry a short-lived Entra continuation token.
- MSAL V1 does not surface the resend wait (`interval`) or any throttling check; throttling can only be
  recognised from the raw `subError`/`error` strings.

## Decision

### Account non-disclosure (AC3)

An unknown account is handled **exactly like "code sent"**: the client returns a private dead-end token, the
user sees the code screen, and any code submitted against a dead-end token is reported as an *incorrect code*.
The app never shows `sentTo`, `codeLength` or the channel (they exist only for real accounts). The code-screen
guidance covers a mistyped email address.

**Residual risk (to raise with DEFRA Delivery Architecture, `delivery.architecture@defra.gov.uk`, alongside
ADR 0019 Q10/Q11):** Entra's reset API still distinguishes the two cases for a direct caller. The app's UI
masking is an additional layer only; Entra's throttling and smart lockout are the primary defence. A mistyped
email also leaves the user with "incorrect code" until they restart.

**Timing side channel:** an unknown account may answer faster or slower than a real one, which could reveal
existence to a measuring attacker. Not mitigated in the app; raise with Delivery Architecture and confirm in the spike.

### Where reset state lives

The SDK step objects are wrapped in a private class behind the **opaque** domain type `PasswordResetToken`
(an empty interface whose `toString()` reveals nothing). The token is held only in a ViewModel scoped to the
reset navigation graph, is **never saved** (`SavedStateHandle`, `rememberSaveable`, route
arguments, Room, DataStore) and is discarded with the ViewModel. Process death restarts the journey from
Sign in (the root phase has not changed, so the user lands on Sign in).

Rejected alternatives: persisting the Parcelable step (stores a live Entra continuation token at rest and the
email inside it); a singleton repository holding the step (state that outlives the journey and is shared
between users on a shared device); passing the email in the route (leaks into back-stack/log/deep-link state).

### Expired versus incorrect code (Q2)

Show **"expired"** when Entra reports an expired reset session (`expired_token`), **or** when a code is rejected
and more time has passed than the configured code lifetime since it was sent (decided in the ViewModel with a
monotonic clock; the lifetime is read from the packaged config, ADR 0024). Otherwise show **"incorrect"**. The
design's **"already used"** message is dropped: a code cannot be reused in-app, because the journey moves on
once a code is accepted.

### Password rules (Q3)

The app checks **only what Entra checks**, from one `PasswordPolicy(minLength = 8, maxLength = 256,
minCategories = 3)`: not blank, at least 8, at most 256, at least 3 of 4 character categories (lower, upper,
digit, other), and the two entries match. The 3-of-4 rule is stated up front in the hint (GOV.UK). A password
Entra still rejects (banned, recently used) maps to the generic "does not meet the password requirements"
message. The design's separate "must contain an uppercase letter / a number / a special character" errors are
**not** built as must-have-all rules — they would reject passwords Entra accepts (see the design spec's
deviations).

### Browser fallback

`browser_required` at any step hands off to the existing interactive sign-in (`signInInteractively`) behind the
same announced hand-off as sign-in (WCAG 4.1.3, shared `BrowserHandoff`). The hosted page finishes as a
**sign-in**, not a reset; the hand-off message says so. A second `browser_required` after the browser leg is
terminal (no loop), as in `MsalSignInRepository`.

### No automatic sign-in

`ResetPasswordResult.Complete` offers a `SignInContinuationState`; it is **ignored** (AC17 returns the user to
Sign in).

### Layering and MSAL confinement

`MsalNativeAuthClient` remains the **only** file importing `com.microsoft.identity.*` (ADR 0019). It implements
a second narrow port, `NativePasswordResetClient`, whose results (`NativeResetOutcome`) never carry an SDK
message, email or code. Turning SDK checks into outcomes happens in **pure `internal` functions that take plain
booleans/strings** (`PasswordResetErrorMapping.kt`, no MSAL import) so the security-critical mapping is unit
tested to 100%. Evaluation order: browser required → network cause → expired session → throttling (both from
known `subError`/`error` strings, so a server `expired_token` wins over an invalid-code flag) → invalid code /
invalid password → reset failed → safe unknown. For *start*, an unknown or invalid account is checked after the
network cause and returns the dead-end token. The string matching lives in one object (`ResetServerStrings`) so
it can be adjusted after the spike.

### Safe, translatable errors

Auth errors are domain types mapped to `@StringRes` resources in the UI, so the CYM toggle re-translates them
— unlike ADR 0011's hard-coded English messages (out of scope to change here). No SDK text, email, code or
password is ever logged, persisted or placed in a diagnostic.

### Typed values and session expiry

The email field is cleared as soon as a code is sent (AC9); the code field is cleared when the code is accepted.
A wrong or expired code keeps the typed code so it can be corrected. Both password fields are cleared when the
reset completes or fails with a page-level error, but kept when the password is only rejected (so it can be
edited). If Entra reports the session expired on the **new-password step**, the journey restarts at a blank email
screen with a page-level "Your reset has expired. Enter your email address to start again." error and all typed
values are wiped. The code field deliberately has **no autofill content type**: Compose offers only an SMS
one-time-code type, which must not stand in for an email code.

### Password handling

Passwords reach the SDK as a `CharArray`, wiped in a `finally` block by the use case and again by the MSAL
client. The Compose text buffer itself cannot be wiped (accepted limitation, as for sign-in).

### Screenshots and `FLAG_SECURE` (Q11)

`FLAG_SECURE` is set **only on Create a new password** (`SecureScreen`, set on enter, cleared on leave). Applying
it to the other reset screens would stop users capturing an error for support, which the DEFRA diagnostics
guidance expects.

### Other decisions carried from the approved plan

- **No client-side resend wait** (Q10): in-flight taps are ignored; if Entra throttles, show "Wait a few minutes
  and try again". "Send a new code" restarts the journey from a blank email screen and never calls the SDK's
  `resendCode`.
- **Open your email app** (Q4): a tap-only button on the code screen; the app never opens an email app by itself
  (WCAG 3.2). It handles a device with no email app and needs no <queries> entry.
- **Offline** (Q7): an offline notice on Sign in and on each reset screen; actions are blocked while offline.
  The notice action is **"Try again"** (re-checks connectivity), not "Sign in" (design-spec deviation D8).
- **Fake stage**: `FakePasswordResetRepository` (when `USE_FAKE_SIGN_IN`) uses documented values —
  `00000000` expired, `11111111` valid, anything else incorrect, a password containing `fail` rejected.

## Pending spike (gated follow-up)

A throwaway, debug-only device test against the **DEV tenant** (no credentials committed) must verify how each
case actually comes back from MSAL 8.6.0 and update the matching constants if they differ. **Not run** in this
change — no credentials were available. A gated start-only probe exists
(`MsalNativeAuthClientPasswordResetDeviceTest`, needs the `resetProbeEmail` instrumentation argument); the
code, expiry and password cases need a mailbox and remain manual. Behaviours to verify:

1. Unknown account: `isUserNotFound()` vs `isInvalidUsername()` on start.
2. Wrong code: `isInvalidCode()` and `subError` (`invalid_oob_value` expected).
3. Code entered after the lifetime has elapsed (is it `invalid_oob_value` or `expired_token`?).
4. Reset session expired: `subError`/`error` string (`expired_token` expected) and which step returns it.
5. Throttling / too many requests: which result type and `subError`/`error` string carries it.
6. Every password error: `isInvalidPassword()`, `isPasswordResetFailed()`, and their `subError` values
   (e.g. banned, recently used, too short).
7. The real code lifetime (the plan assumed 5 minutes) and how the `OnOtpSend` email content/ownership works.
8. Whether `ResetPasswordError.isBrowserRequired()` fires for tenants without reset enabled.
9. ~~Compose email one-time-code autofill type~~ — resolved: none exists (SMS only), so the code field has no
   autofill type.

**Release gate:** this feature must not ship until the spike has run and the mapping is corrected.

Results are recorded here, not committed as reference data.

## Consequences

- A fully native, bilingual reset journey with no new dependency; MSAL stays at 8.6.0.
- The residual account-existence disclosure in Entra is documented and escalated, not eliminated.
- The expired/incorrect distinction is heuristic until the spike confirms the server behaviour.
- **Release gate:** the DEV-tenant spike below must be run and this ADR moved to Accepted before release.
- The reset-code lifetime and reset email content (subject, body, UKFA contact text) are **Entra-side
  dependencies** owned by the identity/platform team, not this repository.
- ADR 0019 Q2 is closed; the amendment row there links to this ADR.
