# Offline fisheries statistical sub-rectangle map

This document is the practical companion to
[`docs/adr/0013-offline-fisheries-map-rendering.md`](../adr/0013-offline-fisheries-map-rendering.md) (read
that first for the *why*). This page is the *how*: where things live, how to regenerate data, and how the
runtime behaves.

## Why no network map tiles

The catch-record wizard's `WizardStep.GearStatRectangle` map must work with **no connectivity, no map SDK,
no API key, and no network request at all** (DEFRA offline-first requirement + explicit product direction
for this feature). Instead of Google Maps/Mapbox/MapLibre/OSM tiles, the map is a plain Jetpack Compose
`Canvas` drawing pre-processed, bundled GeoJSON geometry — see ADR-0013.

## Source and generated file locations

| What | Where |
|---|---|
| Source GeoJSON (land, sub-rectangles, ports) — **do not move or edit at runtime** | `app/src/main/java/uk/gov/defra/mmocatchrecord/feature/catchrecord/presentation/wizard/map/data/{map,subrectangles,ports}.geojson` |
| Pure Kotlin parsing/geometry/sea-overlap/codec (shared build-time + runtime) | `build-logic/map-data-core/src/main/kotlin/...` |
| Gradle task + plugin that generates the runtime dataset | `build-logic/map-data-plugin/src/main/kotlin/...` |
| Generated compact dataset (per variant, **never committed**) | `app/build/generated/.../assets/map_geometry.bin` → packaged as `assets/map_geometry.bin` in the APK |
| Generated fallback copies of the 3 source files (**never committed**) | packaged as `assets/geo-source-fallback/{map,subrectangles,ports}.geojson` in the APK |
| Runtime repository (loads generated data, falls back on-device) | `app/src/main/java/.../feature/catchrecord/data/map/AssetMapDataRepository.kt` |
| Camera/projection math | `app/src/main/java/.../feature/catchrecord/presentation/wizard/map/MapCamera.kt` |
| Canvas rendering + gestures + accessibility | `app/src/main/java/.../feature/catchrecord/presentation/wizard/map/MapCanvas.kt` |

Files under `src/main/java` are **not** packaged into the APK by AGP's default asset merging — that is
precisely why a build-time task is needed to turn them into real `assets/`.

## Coordinate systems: WGS84 with an EPSG:3857 fallback

Most rings in the bundled GeoJSON are plain WGS84 (longitude/latitude, degrees). A meaningful subset of
`subrectangles.geojson`'s rings are actually **EPSG:3857 (Web Mercator) metres**, miscoded as if they were
lon/lat. Every coordinate pair is validated as WGS84 first (`lon ∈ [-180, 180]`, `lat ∈ [-90, 90]`); if that
fails, it is inverse-projected from Web Mercator (`R = 6378137`; standard spherical inverse formulae, output
clamped to `±85.05112878°`) and re-validated. If it is still invalid, the coordinate/feature is skipped and
counted — this is diagnostic-only, never a crash. See `:map-data-core`'s `CoordinateValidator` and its test
suite for the exact known-value round-trip tests.

## Regenerating the data manually

The generation task is per Android build variant:

```powershell
.\gradlew.bat :app:generateDebugMapData
.\gradlew.bat :app:generateReleaseMapData
```

Either task also runs automatically as a dependency of that variant's asset-merging task, so a normal
`:app:assembleDebug` regenerates the data whenever needed without an extra step.

## How Gradle decides the task is up to date

`GenerateMapDataTask` declares its three GeoJSON inputs as:

```kotlin
@get:InputFiles
@get:PathSensitive(PathSensitivity.RELATIVE)
abstract val geoJsonSources: ConfigurableFileCollection
```

Gradle content-hashes these files (not just their timestamps), so:

- Running the task twice in a row with no changes → the second run is `UP-TO-DATE`.
- Touching a file's *timestamp* only (no byte change) → still `UP-TO-DATE` (verified).
- Changing a file's actual bytes → the task re-executes (verified: appending a byte to `ports.geojson`
  triggered a rerun; reverting the file to its original content and rerunning showed `UP-TO-DATE` again).
- Changing the task/plugin's own compiled code (in `build-logic`) also invalidates the up-to-date check,
  because Gradle includes a task's implementation classpath in its up-to-date fingerprint automatically —
  no extra wiring is required for this.
- A missing/deleted output also forces a rerun (standard Gradle behaviour for any task with declared
  `@OutputDirectory`).

The task is annotated `@CacheableTask` so it also participates in the Gradle build cache when enabled.

## Runtime loading and fallback

`AssetMapDataRepository` (a Hilt `@Singleton`, behind the `MapDataRepository` domain interface) loads once
per process, off the main thread:

1. **Primary path** — read and decode `assets/map_geometry.bin`.
2. **Fallback path** — if that asset is missing, corrupt, or its embedded version tag doesn't match, parse
   `assets/geo-source-fallback/{map,subrectangles,ports}.geojson` on-device using the exact same
   `:map-data-core` parsing code the build task uses (so behaviour is equivalent, just slower on-device).
3. **Both fail** — the repository returns a `Result.failure`, the ViewModel surfaces a retryable error, and
   the Grid step's UI shows an accessible error message with the "Other" (non-map) selection path still
   available. The map failing to load never blocks completing the wizard step and never crashes the app.

Every load is logged via Timber (see ADR-0011) without any raw file contents/PII; the successfully-parsed
dataset is cached in memory for the remaining lifetime of the process.

## Draw order

Bottom → top, single `Canvas` draw pass, viewport-clipped:

1. White sea background.
2. Sub-rectangle grid (`MmoColors.MapGridLine`, thin pale green stroke, no fill).
3. Selected sub-rectangle highlight (`MmoColors.MapSelectedFill` pale blue fill + `MapSelectedStroke` GOV.UK
   blue border; selection is also announced via TalkBack, so it is never colour-only).
4. Land polygons (`MmoColors.MapLand`, dark teal fill + thin black outline) — drawn over the grid so no
   unselected rectangles appear on land.
5. The selected sub-rectangle again, above land, so the **whole** selected cell stays highlighted even where
   it covers land: the same `MapSelectedFill` at 60% alpha (the coastline still reads through it) plus the
   opaque `MapSelectedStroke` border. Over sea this composites to the identical colour as step 3, so only the
   land portion changes.
6. Sub-rectangle labels — plain black text, no halo; viewport-culled; drawn only for sea-overlapping cells
   whose centroid is not on land, and never for the selected cell (step 8 draws that one).
7. Ports — viewport-culled cyan dots, de-duplicated by name and coordinate, each with its name centred beneath
   it in smaller black text over a white halo (ports sit on the coastline, so no single colour passes AA on
   both sea and land). Display-only, never intercept taps. A port's dot is always drawn; its name is skipped
   once its halo's bounding box would overlap an already-placed name (earlier dataset order wins), so names
   of distinct ports close together never collide.
8. The selected sub-rectangle's code — always drawn, bypassing both the sea-only rule in step 6 and the
   visible-label cap. It is drawn last so a port dot or name cannot obscure it, and is clamped into the
   selected cell's own on-screen box, falling back to the viewport when the chip cannot fit there. The code
   is white on a `MapSelectedStroke` (GOV.UK blue) chip — ~5.2:1, independent of whatever it covers — so it
   needs no halo, where black on bare `MapLand` would be ~1.85:1 and fail WCAG 1.4.3.
9. A ~1.5dp black border around the whole viewport.

## Selection semantics

- Only sea-overlapping ("coastal") sub-rectangles are selectable; inland ones are outlined but unlabelled
  and cannot be tapped.
- A tap converts screen → geo, filters candidate sub-rectangles by bounding box, then runs a full
  point-in-polygon test (multipolygon-with-holes aware) against the sea-overlapping candidates.
- A tap that lands outside every selectable polygon **clears** the current selection.
- The selected `sub_code` flows into the wizard's existing `selectedCode` state; "Save and continue" submits
  it via the existing `onSubmit(code)` path into `GearUse.statisticalSubRectangleCode` — unchanged from the
  pre-map schematic-grid implementation.
- A plain text line outside the map states the current selection (or its absence) for users who cannot see
  the map's visual highlight, and the map's own accessibility content description also states it; selection
  changes are announced via a polite live region.
- The "Other" button always remains available (even while the map is loading or has failed to load) and
  switches to the fully keyboard/TalkBack-accessible radio-list/autocomplete path — this is the authoritative
  non-map selection method and its behaviour is unchanged by this work.

## How the initial camera is supplied

The camera is computed **once** per gear use (`rememberSaveable` keyed by the gear use id) and is never
reset by recomposition, selection changes, or other state updates — only a configuration change restores it
from the same saved value. `MapCameraSupport.initialCameraFor` derives the initial centre in this order:

1. Match the departure port's **name** (case/whitespace-insensitive) against the `port` property in
   `ports.geojson`.
2. Otherwise, use the bounding box of whichever nearby sub-rectangle codes
   (`MapSupport.nearbyRectanglesFor(...)`) are actually present in the generated dataset.
3. Otherwise, fall back to a documented default centre in UK waters.

## Known data-integration gap: port ↔ reference-data linkage

The domain `Port` model (`ReferenceDataRepository`, ADR-0008's stub) has **no coordinate field**, so camera
placement cannot use it directly — it must be re-matched by name against `ports.geojson`. As checked against
the current stub reference data:

- Both stub ports ("Hastings", "Dover") **are** present by name in `ports.geojson`, so name-matching
  resolves for them today.
- The stub's Hastings sub-rectangle codes (`38E95`, `38E96`, `38E98`, `38E99`, `37E97`, `37F01`, `37F02`,
  `38F02`, `38F03`) do exist in the real `subrectangles.geojson`, but at real coordinates in the north-east
  England / Tyne-Tees area (~54–55°N) — **not** near the real town of Hastings (~50.86°N) — the stub evidently
  chose these purely as plausible-looking code strings, not real geography.
  Dover's placeholder codes are only partly real: `30F09`/`30F11` exist in the real data; `30F10` does not
  exist at all.
- This does not block the map feature (the map derives its own geometry independently of the stub's rect
  codes), but any future test/demo that expects the stub's Hastings codes to appear "near Hastings" on the
  real map would be incorrect — flagged as a governance item against ADR-0008 for whoever next revisits the
  reference-data stub.

**Update (CRAR Other-journey QA fixes):** the radio-list/autocomplete "Other" sub-screens (screens 2/3) no
longer use the stub's `statisticalAreaId` grouping for their nearby list or search/existence validation once
this dataset has loaded — see `MapSupport.nearbyRectanglesFromDataset`/`allRectanglesFromDataset`, which
match the departure port by name against `dataset.ports` (as above) and rank by real centroid distance
instead. The stub-grouped `nearbyRectanglesFor` remains only as the fallback while the dataset hasn't loaded.

## Regenerating this documentation's numbers

Run `.\gradlew.bat :app:generateDebugMapData --rerun-tasks --info` and look for the single summary line the
task logs, e.g.:

```text
MapData: land=7 (skipped 0), subrects=3465 (skipped 0, reprojected-coords=17325, inland=608), ports=938 (skipped 0)
```

## Update (CRAR Track B: coordinates and ICES rectangle display)

`StatisticalSubRectangle` now carries the generated dataset's `centroidLatitude`/`centroidLongitude`/
`icesRectangleName` (via `MapSupport.toStatisticalSubRectangle`), so the "Other" autocomplete search
(screen 3) shows each suggestion's ICES rectangle and centre coordinates (FR5), displays the matched code's
coordinates live as the user types an exact code (FR6), and persists those coordinates alongside the code
on `GearUse` (FR7, DB v8→v9 — see
[ADR-0017](../adr/0017-statistical-sub-area-coordinates-and-ices-rectangle-display.md)). The displayed
coordinates are always the sub-rectangle's **centroid**, not a point the fisher actually fished at — this
convention is flagged in the ADR as a governance item for Delivery Architecture to confirm against the
live Catch Recording service's own definition.
