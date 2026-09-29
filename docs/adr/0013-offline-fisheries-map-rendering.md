# 0013 - Offline fisheries statistical sub-rectangle map rendering

## Status

Accepted

## Context

`WizardStep.GearStatRectangle` ("Where was most of your catch caught using {gear}?") must let the user pick
a ICES statistical sub-rectangle by tapping a map, matching a confirmed iOS screenshot
(`app/screenshots/ios_map_screensot.jpg`). The app must work **fully offline** (Secure by Design / DEFRA
offline-first requirement): no map SDK, no vector/raster tile server, no API key, no network request. The
only inputs available are three bundled GeoJSON files already checked in under
`app/src/main/java/.../wizard/map/data/{map,subrectangles,ports}.geojson` (land polygons, ~3.4k statistical
sub-rectangles, ~940 ports). Because these files live under `src/main/java`, they are **not** packaged into
the APK by Gradle's default asset merging — they are source/reference data, not runtime assets.

A prior attempt at this feature (ADR-0013 in an earlier iteration of this repo, commit `95201b9`) was fully
reverted (`09515d1`). Per the explicit user direction for this work, that reverted implementation was not
inspected, restored, or mirrored in structure; this ADR and its implementation are a fresh design.

Key constraints driving the design:

- Thousands of polygons/labels and ~940 ports must render/hit-test responsively — this rules out one Compose
  node per feature (semantics tree size, recomposition cost) and rules out parsing GeoJSON text on every
  launch on a low-end device.
- The three GeoJSON files mix coordinate reference systems: most rings are plain WGS84 lon/lat, but a
  significant number of sub-rectangle rings are actually **EPSG:3857 (Web Mercator) metres** miscoded as if
  they were lon/lat pairs (values far outside ±180/±90). Numeric fields are sometimes JSON strings, not
  numbers.
- The project is a **single `:app` Gradle module** (`settings.gradle.kts` has no other included subprojects)
  with AGP 9.1.1 / Kotlin 2.4.10 / JDK 21 toolchain, ktlint + detekt + Kover already wired at the root.
- The tech-stack confirmation gate (`android-developer.agent.md` §5.1) explicitly authorised "custom Compose
  Canvas rendering + build-time preprocessing of bundled GeoJSON, no map SDK/tiles/API key" for this feature
  (developer confirmation recorded 2026-09-29).

## Decision

### 1. Rendering: a single custom Compose `Canvas`, no map SDK

`MapCanvas.kt` draws the whole map (land, grid, selection highlight, labels, ports) in one `Canvas` draw
pass per frame, using precomputed/cached geometry (`MapCanvasGeometry`, built once per dataset via
`remember(dataset)`), not one Compose node per feature. The canvas exposes exactly **one** semantics node
(a content description summarising "what this is + current selection + how to use the alternative list"),
a polite live region announcing selection changes, and forwards taps/drags/pinches through a single
`Modifier.pointerInput` gesture handler (`handleMapGestures`) that disambiguates tap-to-select from
pan/zoom and consumes the gesture so it does not fight the wizard scaffold's vertical scroll.

### 2. Build-time preprocessing via a `build-logic` composite Gradle build

Because the project is a single `:app` module (no existing multi-module Gradle setup to hang a
`buildSrc`-based task off cleanly, and `buildSrc` cannot easily be depended on as a *runtime* library from
`:app`, only as build-script classpath), preprocessing logic is split into a small **composite build**
(`build-logic/`, wired via `pluginManagement { includeBuild("build-logic") }` in the root
`settings.gradle.kts`) with two Kotlin/JVM modules:

- **`:map-data-core`** — pure Kotlin, **no Gradle or Android dependencies whatsoever** (only
  `kotlinx-serialization-json`, already a pinned dependency in `gradle/libs.versions.toml`). Contains GeoJSON
  parsing, the WGS84/EPSG:3857 coordinate validation+reprojection, geometry helpers (bbox, centroid,
  point-in-polygon, multipolygon/hole support), sea-overlap sampling, and the compact generated-dataset
  model + its binary/JSON codec. Because it has no Gradle types, it is usable from **both** the Gradle task
  (build-time) and the Android app (runtime fallback parser) with **zero duplication** — `:app` depends on it
  as an ordinary `implementation("uk.gov.defra.mmocatchrecord.mapdata:map-data-core")` composite-build
  substitution (same group/module coordinates as the composite build's own `group`/project name; Gradle
  transparently substitutes the local build, no version needed), and it is unit-tested independently of
  Android/Robolectric/instrumentation.
- **`:map-data-plugin`** — a `Gradle TestKit`-style plugin module (depends on `:map-data-core` **and** the
  Android Gradle Plugin/Gradle API) exposing a `@CacheableTask` (`GenerateMapDataTask`) and a
  `Plugin<Project>` that registers one such task per Android variant and wires its output directory into
  that variant's generated assets via the AGP variant API:
  `androidComponents.onVariants { variant -> variant.sources.assets?.addGeneratedSourceDirectory(task,
  GenerateMapDataTask::outputDir) }`. This produces `:app:generateDebugMapData` /
  `:app:generateReleaseMapData` tasks, each an implicit dependency of that variant's asset-merging task.

The task:

- Declares its three GeoJSON inputs with `@InputFiles @PathSensitive(PathSensitivity.RELATIVE)` (content-hash
  based; reruns when file *content* changes, not just mtime — verified: touching a file with no content
  change stays UP-TO-DATE, appending real bytes reruns it).
- Because it is a normal Gradle task backed by `:map-data-plugin`'s compiled classes, Gradle's task
  up-to-date checking also automatically reruns it if the plugin/task code itself changes (classpath is part
  of the task's implementation fingerprint) — no extra wiring needed for "reruns when the task's code
  changes".
- Parses/validates/reprojects every feature via `:map-data-core`, computes each sub-rectangle's bbox +
  label centroid (from its own ring geometry, **never** `stat_x`/`stat_y` — see Requirement 2) and its
  sea-overlap flag (§ below), and serialises the result as a **compact custom binary format**
  (`GeneratedMapDatasetCodec`, plain Kotlin `DataOutputStream`-style writer/reader, no Android types) to
  `map_geometry.bin` in the generated assets directory. Malformed features are **skipped, not fatal** — a
  running count + reason is logged (`MapData: land=7 (skipped 0), subrects=3465 (skipped 0,
  reprojected-coords=17325, inland=608), ports=938 (skipped 0)`), never throwing.
- Also **copies the three source GeoJSON files unmodified** into a `geo-source-fallback/` subfolder of the
  same generated assets directory, so the runtime fallback parser (below) has something to read even if the
  compact binary is ever missing/corrupt, without needing to bundle the `src/main/java` files a second way.

Generated output only ever lands under `build/` (`app/build/generated/mapData/<variant>/...` before
merging, then packaged as ordinary `assets/` in the APK) — nothing generated is committed to source control.

### 3. Runtime loading with fallback, never a network path

`AssetMapDataRepository` (Hilt `@Singleton`, implementing the domain-layer `MapDataRepository` interface —
consistent with ADR-0002's MVVM/clean-architecture boundary) loads once per process, off the main thread
(`Dispatchers.IO`), and caches the successfully-parsed `MapDataset` in memory for the remainder of the
process (verified by a unit test asserting `assertSame` across repeated calls). Load order:

1. Read `assets/map_geometry.bin` and decode it with `:map-data-core`'s codec. A version tag is embedded in
   the header; a mismatch is treated the same as "corrupt".
2. If that asset is **missing, corrupt, or version-mismatched**, fall back to parsing
   `assets/geo-source-fallback/{map,subrectangles,ports}.geojson` on-device, using the *exact same*
   `:map-data-core` parsing/geometry code the build task uses (no duplicated logic, so the fallback produces
   an equivalent dataset, only slower).
3. If **both** fail, the repository returns `Result.failure(MapDataUnavailableException)`; nothing throws
   out of the repository. `MapDataViewModel` surfaces this as a retryable `UiStatus.Error`; `MapScreen`'s
   Grid mode renders an accessible error message (`R.string.gear_stat_rectangle_map_unavailable`) with the
   "Other" (RadioList) action still available — the map is never a hard blocker to completing the wizard
   step. All failures are logged via Timber (ADR-0011 structured logging), with no PII/raw file contents.

### 4. Coordinate handling: WGS84 passthrough, else EPSG:3857 inverse projection

Per coordinate pair, `:map-data-core`'s `CoordinateValidator`:

1. Tries the pair as WGS84 lon/lat directly (`lon` in `[-180, 180]`, `lat` in `[-90, 90]`).
2. If invalid, treats the pair as **EPSG:3857 (Web Mercator) metres** and inverse-projects using the
   standard spherical Web Mercator formulae (`R = 6378137`; `lon = x / R * 180 / π`;
   `lat = (2 * atan(exp(y / R)) - π / 2) * 180 / π`, clamped to the Mercator's own valid latitude range
   `±85.05112878°`), then re-validates the result as WGS84.
3. If still invalid, the coordinate — and the feature it belongs to — is rejected and counted as skipped
   (never silently produces garbage geometry).

Both numeric-JSON and numeric-string coordinate/property values are accepted (several GeoJSON properties in
the bundled data are stringified numbers).

### 5. Camera & projection: cos(lat)-scaled equirectangular, explicit one-shot initial camera

`MapCamera`/`CameraMath` (`MapCamera.kt`) implements a simple, well-understood
**cos(lat)-scaled equirectangular projection** (not full Web Mercator) for screen↔geo conversion: X scales
linearly with longitude scaled by `cos(centreLatitude)` to correct for meridian convergence, Y scales
linearly with latitude — sufficiently accurate at the sub-rectangle (≈1° grid) scale used here, cheaper than
Mercator's `atan(sinh(...))`, and symmetric/invertible in closed form (needed for tap hit-testing). Camera
state is a small immutable `MapCamera(centreLon, centreLat, metresPerPixel)` value type with a
`Saver` (`rememberSaveable(saver = MapCamera.Saver, key = "map-camera-$gearUseId")`), so it survives
configuration change and is **keyed per gear use** (each looped `GearUse` gets its own remembered camera).
The initial camera is supplied **exactly once** by the caller (`MapCameraSupport.initialCameraFor`, called
from `MapScreen`'s state holder) and is never recomputed/reset on recomposition, selection change, or any
other state update — `rememberSaveable`'s initializer lambda only runs once per key.

`MapCameraSupport.initialCameraFor` derives the initial centre in this priority order (documented in code
and in `docs/development/offline-map.md`):

1. Match the departure `Port.name` (case/whitespace-insensitive) against the `port` property in
   `ports.geojson`, and centre on that port's coordinate.
2. Otherwise, compute the bounding box of whatever `MapSupport.nearbyRectanglesFor(...)` sub-rectangle codes
   are actually found in the generated dataset, and centre on that bbox.
3. Otherwise, fall back to a documented, sensible default centre in UK waters.

### 6. Sea-overlap, draw order, and colours

Sea-overlap is precomputed once at build time (and, in the fallback path, once at runtime) per
sub-rectangle: a 5×5 sample grid across the sub-rectangle's bbox, pre-filtered against the land polygon's
own bounds before running point-in-polygon, is "inland" only if **every** valid sample point falls inside a
land polygon; otherwise it is "coastal"/sea-overlapping and selectable. Inland sub-rectangles are still
drawn (outline only) but are unlabelled and not selectable — this matches real fishing-ground constraints
(no fisher selects a sub-rectangle with no sea in it).

Draw order (bottom → top), each a Design System colour token added to `common/design/Color.kt`/`MmoColors`
rather than hardcoded in the canvas: white/pale sea background → land (`MmoColors.MapLand`, `#0B4143` fill +
thin black outline) → sub-rectangle grid (`MmoColors.MapGridLine`, `#0B6B3A` stroke, ~6% alpha fill) →
selected highlight (`MmoColors.MapSelected`, `#E8A63A`, ~35% fill, ~3× stroke width) → zoom-gated/culled
labels (never drawn for inland cells; the selected cell's label renders as a white bold pill, so selection
is never colour-only) → zoom-gated/culled ports (`MmoColors.MapPort`, `#01FEE2` dot + haloed name text). A
~1.5dp black border frames the whole viewport; all drawing is clipped to the viewport.

### 7. Interaction & performance

Ports are display-only: a single draw pass over a sequence of already-culled/visible ports
(`.asSequence().map{}.filter{}.take(MAX_VISIBLE_PORTS).forEach{}` — no per-port Compose nodes, so ports never
intercept taps and adding hundreds of them costs one draw call each, not one node each). Tap handling
converts the tap's screen coordinate to geo, filters sub-rectangle bbox candidates, then does a full
point-in-polygon test (multipolygon-with-holes aware) against only the **sea-overlapping** candidates; a tap
outside every selectable polygon clears the selection. Precomputed bounds/centroids/paths mean a selection
change or camera pan/zoom never re-parses or rebuilds the whole geometry set — only the `remember(dataset)`
block does that (dataset identity is stable for the process lifetime once loaded), and drawing itself only
iterates the viewport-culled subset of features every frame.

## Consequences

- **`build-logic/` is a new composite build directory.** It is a genuinely separate Gradle build
  (`pluginManagement { includeBuild(...) }`), not a subproject of `:app`'s own `settings.gradle.kts`
  `include(...)` graph — this is the standard, supported mechanism for sharing plain-Kotlin code between a
  custom Gradle task and application runtime code without introducing a second unrelated build system, and
  without forcing the whole app into a multi-module Gradle layout it does not otherwise have.
  `:app/build.gradle.kts` referencing `implementation("uk.gov.defra.mmocatchrecord.mapdata:map-data-core")`
  (composite-build substitution, no version) trips Android Lint's `UseTomlInstead` check as a **warning**
  (it does not recognise composite-build coordinate substitution as distinct from an external Maven
  coordinate); this is an accepted, documented exception, not a regression to silence via a lint
  suppression, since the dependency genuinely has no version to pin in the catalog.
- `:map-data-core` and `:map-data-plugin` are covered by their own JUnit4 test suites, run as part of the
  composite build; `:app`'s own tests cover the Hilt repository/view-model/camera/canvas-adjacent logic. No
  screenshot-testing tooling was added (explicitly out of scope).
- The generated `map_geometry.bin` + `geo-source-fallback/*.geojson` assets are **only** produced under
  `build/`, are not committed, and are automatically regenerated by any build that runs asset merging;
  see `docs/development/offline-map.md` for exact commands and up-to-date semantics.
- **Data-linkage gap (unresolved, documented, out of scope to fix here):** the app's domain `Port` model
  (`ReferenceDataRepository`/ADR-0008's stub) has no coordinate, so the initial camera cannot be derived
  directly from it — it must re-match the port by **name** against `ports.geojson`. The stub reference-data
  repository's two ports ("Hastings", "Dover") **are** present by name in `ports.geojson`, so this path
  resolves for both currently-testable stub ports. However, the stub's confirmed Hastings sub-rectangle
  codes (`38E95`, `38E96`, `38E98`, `38E99`, `37E97`, `37F01`, `37F02`, `38F02`, `38F03`) all **do exist** in
  the real `subrectangles.geojson`, but at real-world coordinates in the **north-east England / Tyne-Tees**
  area (`ICESNAME` `37E9`/`38E9`/`37F0`/`38F0`, latitude ≈54–55°N), **not** near the real town of Hastings
  (≈50.86°N on the south coast) — the stub's codes were evidently chosen for their string values only, not
  their real geography. Dover's placeholder codes are only partially real: `30F09` and `30F11` exist in the
  real data (near Dover's actual longitude/latitude band), but `30F10` does not exist in `subrectangles.geojson`
  at all. None of this blocks the map feature (camera derivation and sub-rectangle selection both work off
  the real geometry dataset, not the stub's codes), but it means **the stub reference data must not be used
  as a source of "expected" map behaviour** in any future test or demo involving Hastings by name — flagged
  here as a governance item for whoever next revisits ADR-0008's stub data.
