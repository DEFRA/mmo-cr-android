# 0019 - Authentication via Microsoft Entra External ID (native auth)

## Status

Proposed — not yet approved. Supersedes the interim decisions in ADR 0016 Q2 once accepted.

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
   and when requested as part of the sign-in call itself. The cause was never resolved — either
   misconfiguration or a native-auth preview limitation. The PoC shipped with its protected-API scope
   list empty.
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

**The protected API is a separate registration.** The backend API is registered as its own app
registration, exposing its own scope; the client registration takes a delegated permission on it with
admin consent granted. The self-referencing pattern from the PoC is explicitly **not** repeated.

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

**Token storage.** Tokens are **never** persisted by this app's own code. MSAL's own Keystore-backed
cache is the sole store outside memory. `SessionStore` gains a persistent implementation holding only
a last-authenticated timestamp — never a token, token fragment, or account identifier that could
identify a user — via Keystore-wrapped encryption exposed through DataStore, per that interface's
existing contract and consistent with ADR 0006's rejection of `androidx.security-crypto`. No raw token
is ever exposed to UI, logged, or written to a crash report; the PoC's debug token-inspection screens
are **not** ported.

**Offline authorisation is a product decision, not an MSAL one.** MSAL's cache lifetime does not define
how long a field user may keep working offline. A grace period is enforced in our own code by comparing
`SessionStore`'s timestamp against the current time, using the existing `ConnectivityObserver` to
distinguish genuine offline operation from an authentication failure, so the app never falsely reports
a successful sign-in while offline. The PoC defaulted to 7 days; the value for this app is Q4 below.

## Consequences

- A new dependency on the MSAL Android library is added to the version catalog at a **pinned** version.
  Its minSdk, its transitive dependencies, its R8/ProGuard requirements, and whether native auth has
  reached GA must all be verified against current Microsoft documentation at implementation time, not
  assumed from the iOS PoC (which pinned MSAL iOS 2.16.0 purely to dodge a broken release asset).
- MSAL is configured by a **JSON config file in `res/raw`**, not by Kotlin constants. It contains the
  client ID, authority and redirect URI — all non-secret, public client identifiers, so committing it
  is acceptable. It must still be reviewed to confirm no secret is ever added to it.
- The release signing certificate hash must be known before release builds can authenticate, creating a
  **dependency on DevOps and Play App Signing**. Play App Signing re-signs uploads with a different key,
  so the **Play-managed** certificate hash — not just the local upload key's — must be registered.
- `prod`'s reference-data service URL is still deliberately empty (ADR 0016), so this ADR does not by
  itself make `prod` functional.
- CI cannot run authenticated instrumented tests without either test credentials in a gated GitHub
  Environment or a fake binding. The **fake binding is preferred**; no real credential is committed.
- Forgot-password and MFA are **not** in scope here (see Q1/Q2). If either is required, each is a
  substantial piece of work — the PoC sized forgot-password as comparable to the whole sign-up flow.
- ADR 0016's Q2 moves from **Open** to resolved-in-principle on acceptance of this ADR, and to resolved
  in fact once the token spike passes.

## Open questions

| #  | Topic | Status |
| -- | ----- | ------ |
| Q1 | Is MFA required for the catch-record user flow? | **Open** — product decision. If yes, it must be built; the PoC explicitly does not support it. |
| Q2 | Is self-service forgot-password in scope? | **Open** — product decision. Not built in the PoC. Significant work. |
| Q3 | Who owns the backend API app registration, and what is its scope name? | **Open** — blocks the token spike. |
| Q4 | Offline authorisation grace period | **Open** — PoC used 7 days; needs a product/security decision for field use. |
| Q5 | Which custom attributes does the sign-up user flow collect? | **Open** — the client renders them dynamically, so this does not block code, but it does affect UX and testing. |
| Q6 | Does the app need role/permission authorisation from token claims? | **Open** — the PoC models authentication state only, with no concept of roles. |
| Q7 | Is there a separate TEST/PROD External ID tenant? | **Open** — only a DEV tenant is documented. Affects the flavor matrix. |
| Q8 | Confirm the tenant ID from the tenant's own OIDC discovery document | **Open** — the PoC warns that assumed tenant IDs caused real confusion; verify independently. |
| Q9 | Native auth GA status and current MSAL Android version | **Open** — verify before pinning; it was public preview at PoC time. |

## Out of scope

- Catch-record submission API authentication beyond supplying a bearer token.
- Backend-side token validation. The PoC's hand-rolled on-device JWT validator is a client-side
  demonstration only and is explicitly **not** a backend reference implementation; the backend should
  use a maintained JWT/OIDC library for its stack.
- Certificate pinning — still owned by ADR 0016 Q9 (DEFRA Cloud Mobile Services consultation).
- Biometric app-lock re-entry, already covered by `core/security`. It gates **re-entry to the app**, a
  separate concern from obtaining a token from Entra.
