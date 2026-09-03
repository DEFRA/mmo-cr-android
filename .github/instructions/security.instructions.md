---
description: "Security standards for the MMO Catch Recording Android app: DEFRA Secure by Design, OWASP MASVS, Android Keystore, TLS/network security config, data-at-rest protection, authentication, secrets management, error logging. Use when handling data, networking, auth, storage, or reviewing security."
applyTo: "**/*.kt, **/AndroidManifest.xml, **/*.gradle, **/*.gradle.kts, **/network_security_config.xml"
---

# Security standards

Precedence: DEFRA security > GDS > OWASP MASVS/Android. DEFRA services must follow
**[Secure by Design](https://www.security.gov.uk/guidance/secure-by-design/principles/)** principles and
DEFRA [security standards](https://defra.github.io/software-development-standards/standards/security_standards/).
Design the app's complete security profile **before** finalising scope/MVP. For access to non-public
DEFRA systems, the security profile must be agreed with the **Cloud Mobile Services** team.

Mobile devices are easily lost/stolen and often use public networks — treat the device as potentially
insecure.

## Encryption in transit (mandatory)

- **All traffic must be encrypted** (HTTPS/TLS). Never use plain HTTP.
- Enforce a **network security config** that sets `cleartextTrafficPermitted="false"` (and
  `android:usesCleartextTraffic="false"` in the manifest). Justify and scope any exception and raise it
  through governance.
- Consider **certificate pinning** for sensitive endpoints (network security config `<pin-set>` or an
  OkHttp `CertificatePinner`). For internal DEFRA back-ends, a per-app VPN (via MDM) may be required —
  confirm with Cloud Mobile Services.

## Data at rest

- Store secrets/tokens/keys using the **Android Keystore** system (hardware-backed where available), and
  encrypt stored blobs with a vetted library such as **Tink**. Never in plain `SharedPreferences`,
  resources or source. (Note: `EncryptedSharedPreferences` / Jetpack Security Crypto is deprecated — prefer
  Keystore-backed keys with Tink and DataStore.)
- Protect sensitive local/offline data: rely on the app sandbox (internal storage, never external for
  sensitive data) and encrypt the local store (e.g. SQLCipher for Room) where feasible. Minimise data
  retained on device.
- Do not log or cache sensitive personal data. Apply data minimisation.

## Authentication

- Prefer platform-secure auth (OAuth 2.0/OIDC with PKCE via AppAuth/Custom Tabs). Store tokens in the
  Keystore-backed store; refresh securely; support biometric unlock (`BiometricPrompt`) where appropriate
  with a device-credential fallback. Consider **Credential Manager** for sign-in.
- For known external users, an "by invitation" pattern via a recorded email/address may be sufficient —
  confirm identity-assurance requirements with security. Do not build bespoke identity proofing without advice.

## Secrets management

- **Never commit** API keys, keystores, upload keys, `.jks`/`.keystore`, service-account JSON, or
  passwords. Use CI encrypted secrets, `.gitignore` and the
  [secrets Gradle plugin](https://github.com/google/secrets-gradle-plugin). If a secret leaks, follow the
  DEFRA [credential exposure](https://defra.github.io/software-development-standards/processes/credential_exposure/) process immediately.
- Enable **GitHub Advanced Security** (secret scanning, Dependabot) and DEFRA SonarCloud.

## Error logging & diagnostics (DEFRA mobile requirement)

- The app **must log errors** and let a user share diagnostics for support (e.g. exportable logs or, at
  minimum, a screenshot-able error). Support a configurable **debug** log level.
- Use structured logging (e.g. Timber over `Logcat`) with appropriate redaction. Strip verbose logging
  from release builds (`Timber` debug tree only in debug). Never log secrets or personal data in plaintext,
  and be aware logcat is a shared resource.

## Secure coding (OWASP MASVS-aligned)

- Validate and sanitise all input at boundaries; never trust remote or on-device data blindly. Use
  parameterised queries (Room) — never string-concatenated SQL.
- Avoid insecure APIs; keep dependencies patched (Gradle version catalog, pinned, vetted).
- No hard-coded credentials or debug backdoors in release builds. Enable R8/ProGuard minification and
  resource shrinking for release.
- Keep exported components (`android:exported`) minimal and permission-protected; prefer explicit intents;
  set `exported=false` unless a component must be public. Use the Play Integrity API where app/device
  integrity matters.
- Follow least-privilege for permissions (location, camera, etc.); request only what's needed at runtime
  and explain why.
