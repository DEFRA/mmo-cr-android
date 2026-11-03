# 0019 - Authentication via Microsoft Entra External ID (native auth)

## Status

Accepted — 2026-11-02. Supersedes the interim decisions in ADR 0016 Q2 (which moves to
**resolved-in-principle**; resolved in fact only once the Phase 1 token spike passes).

Amended 2026-11-02 — see [Amendments](#amendments-2026-11-02). The amendments change the **token
audience** decision, close Q9, add Q3b and Q10, bring mandatory **web fallback** into scope, downgrade the
self-referencing-scope lesson, and correct the encryption mechanism from Tink to ADR 0006's non-auth-bound
`AndroidKeyStore` AES-GCM.

## Context

Authentication has been interim since ADR 0016: `local`/`dev` bind `StubTokenProvider` (fixed literal
`"local-stub"`, matching DEV's always-allow auth stub) and `prod` binds `MissingTokenProvider`
(returns `null`). ADR 0016 recorded this as open question **Q2 (real token issuer)**. The sign-in
feature is likewise a placeholder: `FakeSignInRepository` always succeeds, and `SessionStore` has only
an `InMemorySessionStore` implementation that does not survive process death.

A separate **iOS proof-of-concept** (`entra-id-poc`, documented in its own `TECHNICAL-DOCUMENTATION.md`)
established that MMO's identity provider is **Microsoft Entra External ID** (CIAM), tenant subdomain
`EXTIDMMOExternalTenantDev`, reached at `https://<subdomain>.ciamlogin.com`. The PoC proved **native
authentication** works — the app renders its own sign-in/sign-up screens and submits credentials
directly to Entra, with no browser or web view. This matters for this app because a browser-redirect
flow is a poor fit for the offline-first field use case and breaks the GDS-patterned screens already
built in `feature/signin`.

The PoC is iOS/SwiftUI and shares **no code** with this repository. What transfers is the tenant
configuration, the flow design, and the lessons — notably two expensive ones:

1. A **self-referencing API scope** (`api://<client-id>/access_as_user` exposed on the *same* app
   registration used as the client) was rejected by Entra, both when requested silently after sign-in
   and when requested as part of the sign-in call itself. The PoC shipped with its protected-API scope
   list empty. **Amended 2026-11-02:** this is a **misconfiguration, not a native-auth preview
   limitation**. Microsoft's documented pattern is a **separate resource app registration** exposing
   `api://<Resource_App_ID>/<Scope.Name>`, with the client holding a **delegated permission** on it and
   **admin consent** granted. The operational instruction that survives is "prove the token in isolation
   first" — not the preview-limitation inference.
2. A default native-auth sign-in returns an access token audienced to **Microsoft Graph**, not to this
   app or any custom API. Only the **ID token** is audienced to the app's own client ID. Neither is a
   usable bearer token for the MMO reference-data or catch-record APIs.

Native auth is only available on External ID (CIAM) tenants; a workforce tenant
(`login.microsoftonline.com`) supports browser-based flows only.

## Decision

Adopt **Microsoft Entra External ID with MSAL native authentication** as the identity provider for this
app, replacing the `FakeSignInRepository` / `StubTokenProvider` / `MissingTokenProvider` placeholders.

**Android-specific registration.** Request a **new, Android-specific app registration** in the same
External ID tenant. The iOS PoC's registration (`54911235-9884-41b9-ba69-8b44ac8e1a1d`, bundle
`uk.gov.defra.entra-id-poc`) is not reusable: MSAL derives the Android redirect URI from the
**applicationId plus the signing certificate's SHA-1 hash**, in the form
`msauth://mmo.catchrecording.android/<url-encoded-base64-sha1>`. Because debug and release builds are
signed with different keys, **each signing key in use needs its redirect URI registered**. The
registration must have public client flows **enabled**, native authentication **enabled**, and **no**
client secret or certificate — a public client cannot hold one.

**The token audience is the gateway / central Authentication Service, pending Q3.** The MMO service
designs supplied by the product team describe a **gateway** model, not a per-service Entra resource
registration that this app calls directly:

- *Catch Recording Service Design* §14.1 — "The gateway supplies approved trusted identity claims… Exact
  claim names and gateway validation rules must be confirmed."
- *Reference Data Service Design* §2.3 — the service "passes the access token to a central Authentication
  Service through the Validation Module", with permission names `reference-data.read` /
  `reference-data.write`. That is a **gateway permission vocabulary**, not Entra's `api://<app-id>/Scope`
  form, so it cannot be assumed to be the scope string MSAL must request.

The client therefore requests a delegated scope on **the gateway's / central Authentication Service's own
app registration**, with admin consent granted. The self-referencing pattern from the PoC is explicitly
**not** repeated. Which registration that is, its Application ID URI, its scope name(s) and the user-ID /
role / scope claim contract are **Q3** — and Q3 is the critical path for this ADR, because *Catch Recording
Service Design* §23 still lists "Gateway user ID, role, and scope claim contract" as an **unresolved
backend decision**. See also **Q3b** (one audience or two).

**Token acquisition is spiked before any UI work.** Acquiring an access token with the correct `aud`
and `scp` for the real API scope is proven in isolation — a throwaway instrumented test or debug screen
that signs in and inspects the decoded token — **before** any production sign-in UI, repository or
interceptor wiring is built on top of it. This sequencing is the direct, explicit recommendation of the
PoC, where discovering the scope failure late cost significant time.

**Layering.** MSAL is confined to the data layer behind the existing domain interfaces, consistent with
ADR 0002:

- `SignInRepository` gains an MSAL-backed implementation; `FakeSignInRepository` is retained for tests
  and the `local` flavor only.
- `TokenProvider` gains an MSAL-backed implementation performing **silent** token acquisition, bound
  for `dev`/`prod`. `AuthInterceptor` needs no change — it already tolerates a null/failed lookup and
  degrades to an unauthenticated request without crashing.
- No MSAL type appears in any domain or presentation class. Sign-in failures continue to map to the
  existing `SignInFailure` sealed class, and `InvalidCredentials` stays deliberately generic so the UI
  cannot be used to enumerate which email addresses exist in the tenant.

**Web fallback is in scope, not optional.** Microsoft states that "all client apps that use native
authentication need to support web fallback" — the SDK hands control to the system browser when a
condition cannot be completed natively (for example a Conditional Access challenge or an unsupported
authentication method). The iOS PoC lists this as an unimplemented gap. Supporting it is therefore
**mandatory** here, and it is the genuine reason the **redirect URI** and the **Play App Signing–managed
certificate hash** registrations matter: pure native authentication never opens a browser, so the redirect
URI exists solely to let the mandated fallback return to the app.

**Token storage.** Tokens are **never** persisted by this app's own code. MSAL's own Keystore-backed
cache is the sole store outside memory. `SessionStore` gains a persistent implementation holding only
timestamps — never a token, token fragment, or account identifier that could identify a user — encrypted
with a **non-auth-bound `AndroidKeyStore` AES-GCM key**, the pattern already established by
[ADR 0006](0006-catch-record-draft-persistence-and-encryption.md) and used by
`CatchRecordPassphraseProvider`, behind a Jetpack **DataStore** `Serializer`. **Tink is explicitly not
used**, and neither is `androidx.security-crypto`: ADR 0006 rejected the latter as deprecated upstream, and
the Keystore pattern it chose is existing repo precedent that adds no new dependency. Non-auth-bound
matters because a background token refresh must never demand biometric re-authentication. No raw token is
ever exposed to UI, logged, or written to a crash report; the PoC's debug token-inspection screens are
**not** ported.

**Offline authorisation is a product decision, not an MSAL one.** MSAL's cache lifetime does not define
how long a field user may keep working offline. A grace period is enforced in our own code by comparing
`SessionStore`'s **`lastVerifiedOnlineAtMillis`** against the current time, using the existing
`ConnectivityObserver` to distinguish genuine offline operation from an authentication failure, so the app
never falsely reports a successful sign-in while offline. That field is deliberately **separate** from
`lastAuthenticatedAtMillis` (which drives app-lock re-entry only): a local biometric unlock must never
renew a remote authorisation decision, or the grace period could be extended indefinitely without the
device ever going online. The PoC defaulted to 7 days; the value for this app is Q4 below.

## Consequences

- A new dependency on the MSAL Android library is added to the version catalog at a **pinned** version.
  **Resolved 2026-11-02 (Q9):** native authentication is **GA**, not public preview; MSAL Android's
  latest release is **8.6.0** (published 2026-10-21 per Maven Central `maven-metadata.xml`) with
  **minSdk 24**, compatible with this app's minSdk 26. Caveat: Native Auth **V1 is the GA surface while a
  V2 API is in private preview** — build against GA V1 and do **not** adopt V2 interfaces. Its transitive
  dependencies and R8/ProGuard requirements must still be verified at implementation time, not assumed
  from the iOS PoC (which pinned MSAL iOS 2.16.0 purely to dodge a broken release asset).
- MSAL is configured by a **JSON config file in `res/raw`**, not by Kotlin constants. It contains the
  client ID, authority and redirect URI — all non-secret, public client identifiers, so committing it
  is acceptable. It must still be reviewed to confirm no secret is ever added to it.
- The release signing certificate hash must be known before release builds can authenticate, creating a
  **dependency on DevOps and Play App Signing**. Play App Signing re-signs uploads with a different key,
  so the **Play-managed** certificate hash — not just the local upload key's — must be registered. This is
  load-bearing specifically because **web fallback is mandatory** (see the Decision): the browser leg of
  the fallback is the only thing that uses the redirect URI.
- `prod`'s reference-data service URL is still deliberately empty (ADR 0016), so this ADR does not by
  itself make `prod` functional.
- CI cannot run authenticated instrumented tests without either test credentials in a gated GitHub
  Environment or a fake binding. The **fake binding is preferred**; no real credential is committed.
- Forgot-password and MFA are **not** in scope for the first slices (see Q1/Q2), but the earlier sizing
  no longer holds: the SDK now **natively supports MFA (email OTP, SMS OTP) and self-service password
  reset**, so the PoC's "MFA not supported" and "forgot-password is comparable to the whole sign-up flow"
  statements are **outdated**. The remaining cost is **UI work**, not SDK or delegate-bridge work.
- ADR 0016's Q2 moves from **Open** to **resolved-in-principle** on acceptance of this ADR, and to
  resolved in fact once the token spike passes. Note the corollary: whatever Q3 resolves to
  **retrospectively changes the already-live reference-data integration**, which currently runs against
  DEV on `StubTokenProvider`'s literal `"local-stub"`.

## Open questions

| #  | Topic | Status |
| -- | ----- | ------ |
| Q1 | Is MFA required for the catch-record user flow? | **Open** — product decision only. The SDK supports email/SMS OTP natively, so the remaining cost is UI, not SDK work. |
| Q2 | Is self-service forgot-password in scope? | **Open** — product decision only. Natively supported by the SDK; the PoC's "comparable to the whole sign-up flow" sizing is outdated. |
| Q3 | Is the token audienced to the gateway / central Authentication Service, and what is its app registration, Application ID URI, scope name(s) and user-ID/role/scope claim contract? | **Open — critical path.** Blocks the Phase 1 token spike and all real-token work. *Catch Recording Service Design* §23 still lists the gateway user ID/role/scope claim contract as an unresolved **backend** decision. |
| Q3b | Do reference-data and catch-record share **one** audience, or are **two** access tokens needed? | **Open** — MSAL supports acquiring multiple access tokens, so two audiences are implementable, but it changes `TokenProvider` from one token to a per-service lookup. |
| Q4 | Offline authorisation grace period | **Open** — PoC used 7 days; needs a product/security decision for field use. Evaluated against `lastVerifiedOnlineAtMillis` only. **Dependency: "surface and handle session-write failure" must close first** — see below. |
| Q5 | Which custom attributes does the sign-up user flow collect? | **Open** — the client renders them dynamically, so this does not block code, but it does affect UX and testing. |
| Q6 | Does the app need role/permission authorisation from token claims? | **Open** — the PoC models authentication state only, with no concept of roles. Overlaps Q3's claim contract. |
| Q7 | Is there a separate TEST/PROD External ID tenant? | **Open** — only a DEV tenant is documented. Affects the flavor matrix. |
| Q8 | Confirm the tenant ID from the tenant's own OIDC discovery document | **Open** — the PoC warns that assumed tenant IDs caused real confusion; verify independently. |
| Q9 | Native auth GA status and current MSAL Android version | **Closed 2026-11-02** — native auth is **GA**; MSAL Android **8.6.0** (2026-10-21), minSdk 24. Caveat: Native Auth **V1 is GA, V2 is private preview** — build against V1. |
| Q10 | Governance: native auth is a conscious deviation from Microsoft's own security preference | **Open — needs owning, not resolving.** Microsoft states "browser-delegated authentication is the more secure option… before you choose native authentication, discuss the security implications with your app's business owner and development team." This ADR chose native auth for offline-first field use and to preserve the GDS-patterned screens already built — defensible, and **no DEFRA or GDS standard mandates browser-delegated authentication**, so this is *not* a DEFRA breach. It is a deviation from vendor security guidance and must be recorded and owned: raise with DEFRA Delivery Architecture (`delivery.architecture@defra.gov.uk`). |

## Known gaps carried from the first implementation slice (2026-11-03)

### Session-write failure must be surfaced and handled (blocks Q4)

`DataStoreSessionStore` reads fail safe: a read `IOException` emits an empty `SessionMetadata`, which means
"never authenticated" and routes the user to sign-in. That is the secure default.

**Writes have no equivalent.** An `IOException` from `recordSignedInNow` / `recordVerifiedOnlineNow`
propagates out of `SessionCoordinator`'s `viewModelScope.launch` with no handler, crashing the app
immediately after a successful sign-in — on a full-storage device in the field, the worst-looking possible
failure.

Swallowing the exception is **not** the fix: unlike a read, a failed write has no safe default. Recording a
sign-in that did not persist leaves `lastVerifiedOnlineAtMillis` stale while the user appears authenticated.

The work is therefore scoped as **"surface and handle session-write failure"**, not "catch `IOException`":
log via Timber (redacted, user-shareable per the DEFRA diagnostics requirement), map to a terminal error
state per ADR 0011's safe error mapping, and keep the user on the sign-in screen with actionable recovery
copy.

**This must close before the Q4 offline grace period ships.** Until a grace period exists, an unpersisted
`lastVerifiedOnlineAtMillis` is cosmetic. The moment authorisation is evaluated against that field, a
silently-failed write becomes a security-relevant correctness bug — the user either keeps offline access
they should have lost, or loses access they are entitled to.

### `recordVerifiedOnlineNow` has no production call site

The method is deliberately unreferenced in `app/src/main`. It is the forward half of the two-timestamp
split: the silent-refresh / token-acquisition path that will call it does not land until the MSAL slice
(Phase 2.3). Only `recordSignedInNow` is wired today.

Detekt, Sonar or IDE unused-member inspections may flag it before that caller arrives. It is intentional —
do not delete it, and do not "use" it from the biometric path, which is precisely the defect the split was
introduced to prevent.

## Amendments (2026-11-02)

Recorded against the original 2026 draft, which was committed unchanged first so these are reviewable as
a diff.

| Area | Change |
| ---- | ------ |
| Token audience | "The protected API is a separate registration" was **incomplete**. Replaced with "the token audience is the gateway / central Authentication Service, pending Q3", citing *Catch Recording Service Design* §14.1 and *Reference Data Service Design* §2.3. |
| Q3 | Reframed from "who owns the backend API app registration" to the audience/registration/URI/scope/claim-contract question, and marked the **critical path**. **Q3b** added for one-audience-vs-two. |
| Q9 | **Closed.** Native auth is GA; MSAL Android 8.6.0 (2026-10-21), minSdk 24. V1 GA / V2 private preview caveat carried. |
| Self-referencing scope | Downgraded from an unexplained failure to a **misconfiguration**. Microsoft's documented pattern (separate resource registration + delegated permission + admin consent) works. The "prove it in isolation first" instruction stands. |
| Web fallback | Added to the Decision as **mandatory and in scope**, and recorded as the real reason the redirect URI and Play-managed certificate hash matter. |
| Q1/Q2 | **Softened.** MFA and SSPR are natively supported by the SDK; remaining cost is UI, not SDK/delegate-bridge work. |
| Q10 | **Added.** Native auth over browser-delegated is a conscious deviation from vendor security guidance — no DEFRA/GDS breach, but must be owned and raised with Delivery Architecture. |
| Encryption | **Tink removed.** `SessionStore` persistence uses ADR 0006's non-auth-bound `AndroidKeyStore` AES-GCM pattern behind a DataStore `Serializer`. ADR 0006 wins: it is existing repo precedent and adds no dependency. `SessionStore`'s own KDoc, which mandated Tink, was corrected in the same change. |
| Status | Moved **Proposed → Accepted**; ADR 0016 Q2 moves to resolved-in-principle. |

## Out of scope

- Catch-record submission API authentication beyond supplying a bearer token.
- Backend-side token validation. The PoC's hand-rolled on-device JWT validator is a client-side
  demonstration only and is explicitly **not** a backend reference implementation; the backend should
  use a maintained JWT/OIDC library for its stack.
- Certificate pinning — still owned by ADR 0016 Q9 (DEFRA Cloud Mobile Services consultation).
- Biometric app-lock re-entry, already covered by `core/security`. It gates **re-entry to the app**, a
  separate concern from obtaining a token from Entra.
