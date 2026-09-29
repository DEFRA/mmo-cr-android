@file:Suppress("detekt.LongParameterList", "detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.map

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.common.design.Spacing
import uk.gov.defra.mmocatchrecord.common.design.govukFocusIndicator
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.GeoBoundingBox
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.GeoPoint
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.LandPolygon
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.MapPort
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.StatisticalSubRectangleGeometry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.map.MapProjection.CameraState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.map.MapProjection.ScreenRect
import kotlin.math.roundToInt

/**
 * The nearby-map's sole selection UI: [StatisticalAreaMapCanvas] drawn with a **per-cell accessible
 * overlay** laid directly over it — there is no separate visible radio list. This is safe **only** because
 * [nearbyGeometry] is always a small, bounded set (typically single-digit-to-low-teens, via
 * `GearStatRectangleSupport.nearbyRectanglesFor`/`nearbyGeometryFor`) — exposing one accessibility node per
 * cell does not carry the "thousands of nodes" risk a full/global ~2,857-cell set would (see ADR 0013).
 *
 * Each nearby cell gets a transparent, positioned, focusable overlay element (no visual of its own — the
 * canvas underneath already draws the cell's border/fill/selected-state, using [StatisticalAreaMapCanvas]'s
 * own default amber selection colours, matching the confirmed reference screenshot) carrying:
 * - [Role.RadioButton] semantics + a `contentDescription` of its code + `selected` state, inside a
 *   [selectableGroup] — the same convention already used by `StatisticalSubRectangleRadioList`/
 *   `GdsRadioGroup` elsewhere in this codebase, so TalkBack/Voice Access/Switch Access announce role, label,
 *   state and group position exactly as those existing patterns do.
 * - A visible [govukFocusIndicator] ring, shown only while focused, positioned over the corresponding map
 *   cell — so sighted keyboard-only/switch-access users see *where* focus is, not just screen-reader users
 *   (WCAG 2.2 AA 2.4.7 Focus Visible / 2.4.13 Focus Appearance).
 * - A touch target sized to the projected cell but never smaller than 48x48dp (WCAG 2.2 AA 2.5.8 Target
 *   Size), even when the projected cell is smaller at low zoom.
 *
 * The overlay and the canvas share **one** hoisted [CameraState] (via [StatisticalAreaMapCanvas]'s optional
 * `cameraState` parameter) and the canvas's reported viewport size, so overlay positions are computed with
 * the exact same [MapProjection] maths the canvas itself draws with — they never drift during pan/zoom.
 * Off-screen cells (after panning) are culled from composition entirely, so they cannot become invisible
 * focus targets.
 */
@Suppress("FunctionNaming", "LongMethod")
@Composable
fun AccessibleStatisticalAreaMap(
    nearbyGeometry: List<StatisticalSubRectangleGeometry>,
    landPolygons: List<LandPolygon>,
    ports: List<MapPort>,
    selectedCode: String?,
    onCodeSelected: (String) -> Unit,
    initialCentre: GeoPoint,
    contentDescription: String,
    cameraResetKey: Any?,
    cellTestTagPrefix: String,
    modifier: Modifier = Modifier,
    canvasTestTag: String = "",
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    // Zoom bounds/initial zoom are derived from the *actual* nearby cell sizes (MapZoomBounds.forCells) —
    // no more than 16 boxes visible at max zoom-out, no fewer than 9 at max zoom-in — using the screen's
    // available width and the map's own fixed height as the best estimate of the eventual viewport size
    // available this early (before the canvas's own onSizeChanged first fires). See MapZoomBounds's doc
    // comment for the full derivation and StatisticalAreaMapCanvas.MAP_HEIGHT for the height constant.
    val zoomBounds =
        remember(nearbyGeometry, initialCentre, configuration.screenWidthDp) {
            val cellSizeDeg = MapZoomBounds.representativeCellSizeDegrees(nearbyGeometry)
            if (cellSizeDeg == null) {
                MapZoomBounds.fallback
            } else {
                val viewportWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
                val viewportHeightPx = with(density) { MAP_HEIGHT.toPx() }
                MapZoomBounds.forCells(
                    cellWidthDeg = cellSizeDeg.first,
                    cellHeightDeg = cellSizeDeg.second,
                    centreLatDeg = initialCentre.lat,
                    viewportWidthPx = viewportWidthPx,
                    viewportHeightPx = viewportHeightPx,
                )
            }
        }
    val cameraState =
        rememberSaveable(cameraResetKey, stateSaver = CameraStateSaver) {
            mutableStateOf(CameraState(centre = initialCentre, zoom = zoomBounds.initialZoom))
        }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val currentCamera by cameraState
    val onCodeSelectedState by rememberUpdatedState(onCodeSelected)
    val minTouchTargetPx = with(LocalDensity.current) { Spacing.minTouchTarget.toPx() }

    Box(modifier = modifier) {
        StatisticalAreaMapCanvas(
            subRectangles = nearbyGeometry,
            landPolygons = landPolygons,
            ports = ports,
            selectedCode = selectedCode,
            onCodeSelected = { code -> if (code != null) onCodeSelectedState(code) },
            initialCentre = initialCentre,
            contentDescription = contentDescription,
            minZoom = zoomBounds.minZoom,
            maxZoom = zoomBounds.maxZoom,
            cameraResetKey = cameraResetKey,
            testTag = canvasTestTag,
            cameraState = cameraState,
            onViewportSizeChanged = { viewport = it },
        )

        if (viewport != IntSize.Zero) {
            Box(modifier = Modifier.matchParentSize().selectableGroup()) {
                nearbyGeometry.forEach { rectangle ->
                    val bounds =
                        MapProjection.projectedBounds(
                            rectangle.boundingBox,
                            currentCamera,
                            viewport.width.toFloat(),
                            viewport.height.toFloat(),
                        )
                    if (bounds.isOutside(viewport.width.toFloat(), viewport.height.toFloat())) return@forEach

                    var hasFocus by remember(rectangle.subCode) { mutableStateOf(false) }
                    val selected = rectangle.subCode == selectedCode
                    Box(
                        modifier =
                            Modifier
                                .cellBounds(bounds, minTouchTargetPx)
                                .semantics(mergeDescendants = true) {
                                    this.contentDescription = rectangle.subCode
                                }.selectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = { onCodeSelectedState(rectangle.subCode) },
                                ).focusable()
                                .onFocusChanged { hasFocus = it.hasFocus }
                                .then(if (hasFocus) Modifier.govukFocusIndicator() else Modifier)
                                .testTag("${cellTestTagPrefix}_${rectangle.subCode}"),
                    )
                }
            }
        }
    }
}

/**
 * Positions and sizes an overlay element in raw pixels (matching [MapProjection]'s pixel-space maths
 * directly, with no Dp round-trip) to the cell's projected [bounds], clamped to at least [minSizePx] on
 * each axis so a small/zoomed-out cell still meets the 48dp minimum touch target.
 */
private fun Modifier.cellBounds(
    bounds: ScreenRect,
    minSizePx: Float,
): Modifier =
    this.layout { measurable, _ ->
        val width = maxOf(bounds.width, minSizePx).roundToInt()
        val height = maxOf(bounds.height, minSizePx).roundToInt()
        val placeable = measurable.measure(Constraints.fixed(width, height))
        layout(placeable.width, placeable.height) {
            placeable.placeRelative(bounds.left.roundToInt(), bounds.top.roundToInt())
        }
    }

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun AccessibleStatisticalAreaMapPreview() {
    fun previewRectangle(
        code: String,
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double,
    ) = StatisticalSubRectangleGeometry(
        subCode = code,
        parentIcesName = "ICES-$code",
        rings =
            listOf(
                listOf(
                    GeoPoint(minLat, minLng),
                    GeoPoint(minLat, maxLng),
                    GeoPoint(maxLat, maxLng),
                    GeoPoint(maxLat, minLng),
                ),
            ),
        bboxCentroid = GeoPoint((minLat + maxLat) / 2, (minLng + maxLng) / 2),
        boundingBox = GeoBoundingBox(minLat, maxLat, minLng, maxLng),
        seaOverlapping = true,
    )
    val nearbyGeometry =
        listOf(
            previewRectangle("38E95", 50.0, 51.0, 0.0, 1.0),
            previewRectangle("38E98", 51.0, 52.0, 0.0, 1.0),
        )
    MmoTheme {
        AccessibleStatisticalAreaMap(
            nearbyGeometry = nearbyGeometry,
            landPolygons = emptyList(),
            ports = listOf(MapPort(name = "Hastings", location = GeoPoint(lat = 50.855, lng = 0.573))),
            selectedCode = "38E95",
            onCodeSelected = {},
            initialCentre = GeoPoint(lat = 50.855, lng = 0.573),
            contentDescription = "Nearby statistical sub-area map preview",
            cameraResetKey = "preview",
            cellTestTagPrefix = "nearby_cell_preview",
        )
    }
}
