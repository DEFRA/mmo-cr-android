# 0013 - Offline statistical sub-area map: custom Canvas, build-time precompute, no map SDK

## Status

Accepted

## Context

The gear/catch statistical sub-rectangle ("ICES statistical sub-rectangle") selection screen needs an
interactive map so a user can visually locate and select the sub-area where the majority of their catch was
caught, backed by three real-world GeoJSON datasets (land polygons, ~3,465 statistical sub-rectangles, 938
ports). The app is **offline-first** (recording catches at sea, often with no connectivity at all) and this
feature must never depend on a live network connection.

This raises a §5.1 tech-stack gate decision (map rendering approach) plus three build/runtime design
questions:

1. **How is the map rendered?** A conventional Android map screen uses a map SDK (Google Maps, Mapbox,
   MapLibre, OSMDroid) that fetches network tile imagery. That is unacceptable here: it requires network
   access (violates offline-first), typically requires an API key/billing account (a third-party dependency
   and potential secret), and is disproportionate for rendering ~3,465 simple polygons plus 7 land shapes and
   938 point markers with no street-level basemap requirement.
2. **How is the source GeoJSON turned into something an app can render performantly?** The three source
   GeoJSON files are large (thousands of features, arbitrary-precision coordinate strings, some fields as
   numeric-looking strings) and are not suitable to parse on-device on every app start, nor to ship raw in
   the APK/AAB (unnecessary size, and needless parsing/allocation cost on low-end devices at sea).
3. **How accessible can a freehand hand-drawn Canvas map be?** WCAG 2.2 AA is a legal requirement; a
   Canvas-drawn map cannot expose thousands of individual polygons as distinct accessible semantics nodes
   without becoming unusable for TalkBack users (or catastrophic for semantics-tree performance).

## Decision

### 1. Custom Jetpack Compose `Canvas` rendering — no map SDK, no network tiles

Render the map with a low-level stateless Compose `Canvas` primitive (`StatisticalAreaMapCanvas`) that draws
directly from decoded domain geometry: a blank base, land polygon fills, the sub-rectangle grid (with an
amber selected-state), zoom-gated `sub_code` labels, and zoom-gated non-interactive port markers. There is
**no** Google Maps / Mapbox / MapLibre / OSMDroid dependency, and the feature makes **zero** network calls at
any point (verified by an automated test asserting no network-permission-requiring code path is exercised in
this feature's tests, and by there being no network client wired into `MapGeometryRepository` at all).

This was confirmed as an explicit §5.1 tech-stack deviation/approval on **2026-09-18** (developer confirmed
via orchestrator, alongside the plan's other four open decisions) — recorded in the Android Developer agent's
tech-stack confirmation block.

### 2. Build-time precompute pipeline → compact derived binary asset, decoded at runtime with a live-parse fallback

A Gradle task (`precomputeMapGeometry`, wired via `androidComponents.onVariants.addGeneratedSourceDirectory`)
runs at build time, reading the three source GeoJSON files from an **unshipped** `app/geo-source/` input
directory and writing a single compact versioned binary asset (`assets/map_geometry.bin`, magic `MMOGEOV1`)
bundled into the APK/AAB. The pipeline:

- Tolerates numeric-looking string fields in the source GeoJSON (defensive parsing, not a strict schema).
- Reprojects coordinates that are not already WGS84 lat/lng by detecting and inverse-projecting Web Mercator
  (EPSG:3857) coordinates as a fallback, so a mixed/inconsistent source coordinate reference system doesn't
  silently corrupt geometry.
- Flags each sub-rectangle as sea-overlapping via 5×5 point-in-polygon sampling against the land polygons —
  only sea-overlapping sub-rectangles are selectable (hit-testable) at runtime, since a purely landlocked
  statistical sub-rectangle can never be where a catch was made.
- Computes each sub-rectangle's **own bounding-box centroid** for its on-map label position — never the
  shared source `stat_x`/`stat_y` fields, which are not distinct per sub-rectangle and would cause label
  collisions.
- Is **incremental**: the task is a no-op (`UP-TO-DATE`) when the three source files are unchanged, so
  routine builds are not slowed down by re-running the pipeline.
- Keeps the raw GeoJSON **out of the shipped app** — only the derived binary asset is bundled.

At runtime, `AssetMapGeometryRepository` decodes `assets/map_geometry.bin` via `MapGeometryBinaryDecoder`,
which fails closed (`Result.failure`, never an uncaught exception) on a missing, corrupt, out-of-bounds, or
version-mismatched asset (see "Defensive decoder bounds" below).

**Genuine on-device GeoJSON fallback (supersedes an earlier "degrade to empty dataset" implementation).**
An initial implementation of this decision, when the derived binary asset was missing/corrupt, degraded to
an **empty** `MapGeometryDataset` rather than a real re-parse — reasoned at the time as "raw GeoJSON is
never shipped in the app, so there is nothing to re-parse". A code-review pass on the shipped feature (2026)
found this did not actually satisfy the approved plan's "graceful fallback to live-GeoJSON-parse" decision,
and that a *fully* missing/unusable map+list on a real device is a materially worse offline-first outcome
than a slower on-device parse. This has been corrected as follows:

- The three raw source GeoJSON files are **also** bundled as app assets, under
  `assets/geo-source-fallback/` — copied there by the same `GeoPrecomputeTask` that writes
  `map_geometry.bin`, from the same unshipped `app/geo-source/` input directory. This is a deliberate,
  flagged exception to "raw GeoJSON is never shipped": the three files add ≈2.6MB to the derived-assets
  output (versus ≈716KB for the compact derived binary — confirmed via a real `precomputeMapGeometry` run),
  accepted as the cost of a fallback path that can genuinely produce usable geometry rather than an empty
  map+list. The compact derived binary asset remains the **primary**, always-used-first path; the raw
  GeoJSON is read only when that primary decode fails.
- The pure parse/reproject/sea-overlap/binary-write pipeline logic (`GeoJsonParser`, `Reprojection`,
  `GeometryMath`, `GeoBinaryWriter`, `GeoPrecomputePipeline` — everything except the Gradle-API-dependent
  `GeoPrecomputeTask` itself) was extracted out of `buildSrc` into `shared-geopipeline/src/main/kotlin/`, a
  plain Kotlin source directory with no Gradle-API or Android dependency. Both `buildSrc` and the `app`
  module add this directory as an extra Kotlin source directory (`sourceSets { main { kotlin.srcDir(...) }
  }`), since `buildSrc` is a separate Gradle build and cannot declare a normal `project(...)` dependency on
  an app-module subproject of the root build — this is the standard workaround for sharing pure logic
  between `buildSrc` and the main build without duplicating it.
- `AssetMapGeometryRepository`'s fallback reads the three raw GeoJSON assets as text and feeds them through
  the **exact same** `GeoPrecomputePipeline.run(...)` used at build time, writing into an in-memory
  `ByteArrayOutputStream` in the same binary format, then decodes that via the same
  `MapGeometryBinaryDecoder` used for the primary asset. This reuses 100% of the parse/reproject/
  sea-overlap logic with zero duplicated/divergent implementation, at the cost of a slower (full on-device
  GeoJSON parse) load in the rare fallback case.
- If the fallback GeoJSON parse **also** fails (an unlikely double failure — e.g. the fallback assets are
  also corrupt/missing), `AssetMapGeometryRepository` returns an explicit `Result.failure`
  (`MapGeometryUnavailableException`) rather than an empty dataset, so `CatchRecordFlowViewModel` can
  surface a real, accessible error state (see decision 3 below) instead of silently rendering a broken/empty
  screen.
- The asset-open code path now catches the broad `java.io.IOException` (not just `FileNotFoundException`),
  so any I/O failure opening the primary asset (not only "file not found") routes through the same fallback.

**Defensive decoder bounds.** `MapGeometryBinaryDecoder` validates every length-prefixed collection count
read from the binary stream against a generous, documented ceiling *before* using it to size an
allocation/loop (the real dataset — ~7 land polygons, ~3,465 sub-rectangles, ~938 ports, ~44,000 total
coordinate points — sits comfortably inside every bound with more than 10x headroom), and validates every
decoded coordinate is finite and in-range and every bounding box is correctly ordered (`min <= max`). A
violation of any of these fails the decode closed (routing through the same fallback/error handling above)
rather than risking an out-of-memory allocation, an infinite loop, or geometry that would silently misrender
or misbehave in hit-testing/projection maths.

### 3. The synchronised list is the authoritative accessible path; the map is an enhancement

The map `Canvas` exposes a **single summarised semantics node** (not thousands of individual polygon nodes)
with a content description, and selection changes are announced via a live region. The `GdsRadioGroup`-style
list of sub-rectangle codes beneath the map is bound to the same selection state (two-way sync with the map)
and is the **authoritative WCAG 2.2 AA accessible/keyboard/TalkBack path** — every user, including one who
never touches the map at all, can complete the flow entirely through the list, reusing the existing
`Role.RadioButton`/`govukFocusIndicator`/focus-order/error-summary-focus conventions already established
elsewhere in `GearStatRectangleScreen.kt`. This mirrors the plan's decision that the map is a **non-exclusive
enhanced input** layered on top of an always-fully-functional accessible list, not a replacement for it.

### 4. Hand-rolled versioned binary format, not protobuf/flatbuffers

The derived asset uses a small hand-rolled `DataOutputStream`/`DataInputStream`-based binary format (magic +
version header, then length-prefixed records) rather than introducing a serialization library (protobuf,
flatbuffers, kotlinx-serialization-cbor). The format is simple enough (a handful of fixed record shapes) that
a dependency was judged unnecessary; the magic+version header lets the decoder fail closed and evolve the
format later without breaking older builds silently.

## Consequences

- **No map SDK dependency, no API key/billing account, no network map tiles** — the app satisfies
  offline-first and avoids a third-party service dependency for this feature entirely.
- **~1MB AAB size increase** from the bundled derived geometry asset (confirmed ≈716KB for
  `assets/map_geometry.bin` in a debug build) is accepted as the cost of fully offline, instant-load map
  data — this was an explicit approved trade-off (decision 4 of the approved plan).
- **A further ≈2.6MB from the bundled raw fallback GeoJSON** (`assets/geo-source-fallback/*.geojson`,
  confirmed via a real `precomputeMapGeometry` run: ≈380KB `map.geojson` + ≈173KB `ports.geojson` +
  ≈2,075KB `subrectangles.geojson`) is a **separate, deliberate, flagged exception** to "raw GeoJSON is
  never shipped" (see "Genuine on-device GeoJSON fallback" above) — accepted so the promised fallback path
  can genuinely produce usable geometry on a real device, not just at build time. It is the fallback-only
  path: the compact derived binary remains the primary, always-used-first asset, so this cost is paid in
  APK/AAB size but not in the normal-case load time.
- **The precompute pipeline is a build-time-only concern**: it never runs on-device, and its Gradle task
  inputs/outputs make it incremental so normal builds are not slowed down.
- **Camera behaviour is deliberately restrained**: the map applies its caller-supplied initial centre/zoom
  (the departure port's area) exactly once and never auto-fits or resets on recomposition, so pan/zoom state
  a user has set is never silently discarded — consistent with predictable, non-surprising UI behaviour.
- **Accessibility risk is mitigated by design, not by afterthought**: because the list, not the map, is the
  legally-required accessible path, a future change to the map's visual design does not risk regressing WCAG
  2.2 AA compliance as long as the list keeps working.
- **Simplified on-screen projection**: the on-screen camera projection is a simplified cosine-latitude-scaled
  equirectangular-style projection (not a full Web Mercator projection) for the visible map viewport, since
  the sub-rectangles occupy a geographically narrow area where the distortion difference is negligible for
  point-in-polygon hit-testing and label placement; the Web-Mercator-inverse-projection handling in the
  precompute pipeline (point 2 above) is a **source-coordinate-normalisation** step, not the on-screen camera
  model, and the two are not conflated.
- **Numeric-string tolerant parsing and the Web-Mercator inverse-projection fallback are defensive
  measures** against a source dataset that is not perfectly clean/consistent — they add a small amount of
  parsing complexity in exchange for the pipeline not silently producing corrupt geometry from
  edge-case input.
- Should a future story require richer basemap detail (street-level imagery, live vessel tracking, etc.)
  that a hand-rolled Canvas renderer cannot reasonably support, this ADR should be revisited/superseded
  rather than silently bypassed — introducing a map SDK at that point would need its own offline/security/
  governance review (network access, API keys, third-party data processing).
