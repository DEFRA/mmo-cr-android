# 0014 - HTTP client: Retrofit + OkHttp

## Status

Accepted

## Context

ADR 0008's bundled reference-data stub is being superseded by a real network-backed
`ReferenceDataRepository` implementation (see ADR 0015). This requires a first HTTP client in the app.
DEFRA's standards precedence (`copilot-instructions.md` §5) names no HTTP library, so this decision is
made at the Google/Android-community tier, consistent with the precedence order.

The app already depends on `kotlinx-serialization-json` (ADR 0007's typed Navigation routes), Hilt (ADR
0005), and Kover-via-`testDebugUnitTest` for coverage (only JVM-reachable tests count towards the
SonarCloud gate — see `sonar-project.properties`). Any client choice must have a JVM-side test harness
(no emulator/device required) to keep coverage attributable.

## Decision

Adopt **Retrofit 3.0.0** + **OkHttp 5.5.0** (pinned via `com.squareup.okhttp3:okhttp-bom:5.5.0`) +
**`com.squareup.retrofit2:converter-kotlinx-serialization:3.0.0`**, reusing the existing
`kotlinx-serialization-json` dependency rather than adding Moshi/Gson. Tests use
**`com.squareup.okhttp3:mockwebserver3-junit4`** (also BOM-managed), run under `testDebugUnitTest`/
Robolectric so they count towards Kover/SonarCloud.

Alternatives considered:

- **Ktor client** — a credible, Kotlin-first alternative, but it would be the app's first use of Ktor
  with no existing precedent in this codebase, and its OkHttp/Android engine still sits on top of OkHttp
  for TLS/connection pooling. Retrofit's declarative `@GET`/suspend-fun interface style is simpler to
  test and review for this app's needs (a handful of read endpoints), so it was not chosen.
- **Plain OkHttp (no Retrofit)** — would mean hand-rolling request building, response parsing and
  endpoint interfaces; Retrofit is a thin, well-understood layer over exactly that, so using it reduces
  boilerplate without adding real risk.

All four coordinates were verified to resolve from Maven Central (`retrofit-3.0.0.pom`,
`okhttp-bom-5.5.0.pom`, `converter-kotlinx-serialization-3.0.0.pom`, `mockwebserver3-junit4-5.5.0.pom`)
before being pinned in `gradle/libs.versions.toml`.

### R1 — Retrofit's declared OkHttp version vs the BOM

Retrofit 3.0.0's own POM declares a dependency on `okhttp:4.12.0`. Applying
`implementation(platform(libs.okhttp.bom))` (and `testImplementation(platform(libs.okhttp.bom))`) forces
every OkHttp artifact — including the transitive one Retrofit pulls in — to the BOM's `5.5.0`. This was
verified by inspecting the resolved runtime classpath (see Phase 1 validation report) rather than assumed.
**Documented fallback**, if this genuinely breaks resolution or a transitive API incompatibility surfaces:
drop the `okhttp-bom` platform and pin OkHttp directly to `4.12.0` (matching Retrofit 3.0.0's own
declared version) instead of forcing `5.5.0`. Taking this fallback must be reported back to the developer,
since it changes the pinned OkHttp version recorded here.

## Consequences

- `okhttp-bom`, `okhttp`, `logging-interceptor`, `retrofit`, `converter-kotlinx-serialization` and
  `mockwebserver3-junit4` are new, additive dependencies pinned in the version catalog.
- `HttpLoggingInterceptor` is wired debug-only (see ADR 0015) with the `Authorization` header redacted,
  consistent with ADR 0011's "never log secrets" policy.
- No certificate pinning is added at this stage (see ADR 0015's rationale); this ADR covers only the
  client library choice.
