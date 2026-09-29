@file:Suppress("detekt.LongParameterList", "detekt.LongMethod")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import uk.gov.defra.mmocatchrecord.common.design.MmoColors
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.GeoBoundingBox
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.GeoPoint
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.LandPolygon
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.MapPort
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.StatisticalSubRectangleGeometry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.map.MapProjection.CameraState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.map.MapProjection.ScreenPoint

private val LandFill = Color(0xFF0B4143)
private val LandOutline = Color.Black
private val SubRectangleStroke = Color(0xFF0B6B3A)
private val SubRectangleFill = SubRectangleStroke.copy(alpha = 0.06f)
private val SelectedStroke = Color(0xFFE8A63A)
private val SelectedFill = SelectedStroke.copy(alpha = 0.35f)
private val PortColor = Color(0xFF01FEE2)

// Fallback-only defaults (used by previews/tests and any caller that does not pass its own dynamically
// derived minZoom/maxZoom) — the nearby map itself now derives real minZoom/maxZoom bounds from the actual
// cell geometry and viewport size via MapZoomBounds.forCells, so these two fixed values are no longer the
// production bound; see MapZoomBounds's doc comment for the full derivation.
private const val MIN_ZOOM = 0.4f
private const val MAX_ZOOM = 8f
private const val LABEL_ZOOM_THRESHOLD = 1.5f
private const val PORT_ZOOM_THRESHOLD = 1.5f
private const val LAND_STROKE_WIDTH_PX = 2f
private const val GRID_STROKE_WIDTH_PX = 2f
private const val SELECTED_STROKE_WIDTH_DP = 3
private const val PORT_DOT_RADIUS_PX = 4f
private const val LABEL_TEXT_SIZE_SP = 11
private const val PORT_LABEL_TEXT_SIZE_SP = 9

/**
 * Preserves pan/zoom camera state across configuration change / process death (finding: "camera state not
 * preserved across config change") — flattened to a plain `List<Any>` (a type `Bundle`/`rememberSaveable`
 * can store directly) rather than relying on default `Parcelable`/reflection-based saving, consistent with
 * how [StatisticalAreaMapCanvas]'s caller already saves [selectedCode]-equivalent state.
 *
 * **Precision fix** (finding: "camera Saver silently narrows Double lat/lng to Float"): [GeoPoint.lat]/
 * [GeoPoint.lng] are persisted as [Double] — their actual type — not down-cast to [Float]. Only the
 * genuinely `Float`-typed [CameraState.zoom]/[MapProjection.ScreenPoint] pan offset are stored as `Float`.
 * `List<Any>` is still a plain, directly-`Bundle`-storable saver value type (each element is individually a
 * primitive-boxed `Double`/`Float`), so this loses no round-trip fidelity while still avoiding reflection.
 */
internal val CameraStateSaver: Saver<CameraState, List<Any>> =
    Saver(
        save = {
            listOf(it.centre.lat, it.centre.lng, it.zoom, it.panOffsetPx.x, it.panOffsetPx.y)
        },
        restore = { saved ->
            CameraState(
                centre = GeoPoint(lat = saved[0] as Double, lng = saved[1] as Double),
                zoom = saved[2] as Float,
                panOffsetPx = ScreenPoint(x = saved[3] as Float, y = saved[4] as Float),
            )
        },
    )

/**
 * Low-level, stateless custom map primitive — see ADR 0013 (custom Compose `Canvas`, no map SDK, no
 * network tiles). Draws bottom-to-top: blank base -> land -> sub-rectangle grid (selected = amber) ->
 * zoom-gated code labels at each sub-rectangle's own bbox centroid -> zoom-gated, **non-interactive**
 * ports. Viewport culling is applied to both the sub-rectangle layer (via each polygon's precomputed
 * [uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.StatisticalSubRectangleGeometry.boundingBox]
 * `intersects` check) and the port layer (via [MapViewportCulling.visiblePorts], since a single-point
 * [uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.MapPort] has no bounding box of its own)
 * before each draw.
 *
 * The camera ([initialCentre]/[initialZoom]) is applied **once**, on first composition keyed by
 * [cameraResetKey] (e.g. the current `GearUse.id`) — pan/zoom gestures update it locally afterwards, and it
 * is **never** auto-fit to data extent or reset on recomposition/selection change, per the plan's explicit
 * iOS-parity requirement. It survives process death / configuration change via `rememberSaveable` (see
 * [CameraStateSaver]) exactly like the selection state ([selectedCode], owned by the caller) already does.
 *
 * Accessibility: by default this composable exposes a **single** summarised semantics node
 * ([contentDescription]) — it does not expose per-polygon semantics of its own. The nearby map is this
 * canvas's only caller (`AccessibleStatisticalAreaMap`), which lays an accessible per-cell overlay directly
 * over the canvas instead — see [cameraState]/[onViewportSizeChanged] below, which exist to let that
 * overlay track this canvas's camera/pan/zoom exactly. (The previous full/global map+list screen —
 * `GearStatRectangleMapListContent`, a separate visible radio list beneath this same canvas — was removed
 * once "Other" was changed to jump straight to the free-text Autocomplete screen instead; see
 * `GearStatRectangleScreen`'s doc comment.)
 *
 * [cameraState] and [onViewportSizeChanged] are optional, purely additive hooks: when [cameraState] is
 * `null` (previews/tests) this composable behaves exactly as before, owning its own private
 * `rememberSaveable` camera. When a caller hoists a [MutableState] of [CameraState] in (the nearby map, via
 * `AccessibleStatisticalAreaMap`), this canvas reads and writes that same state instead, so a sibling
 * accessible overlay positioned from the same state never drifts from what is actually drawn.
 */
@Suppress("FunctionNaming", "detekt.CyclomaticComplexMethod", "detekt.LongParameterList")
@Composable
fun StatisticalAreaMapCanvas(
    subRectangles: List<StatisticalSubRectangleGeometry>,
    landPolygons: List<LandPolygon>,
    ports: List<MapPort>,
    selectedCode: String?,
    onCodeSelected: (String?) -> Unit,
    initialCentre: GeoPoint,
    contentDescription: String,
    modifier: Modifier = Modifier,
    initialZoom: Float = 1f,
    minZoom: Float = MIN_ZOOM,
    maxZoom: Float = MAX_ZOOM,
    cameraResetKey: Any? = null,
    testTag: String = "",
    cameraState: MutableState<CameraState>? = null,
    onViewportSizeChanged: (IntSize) -> Unit = {},
    selectedFill: Color = SelectedFill,
    selectedStroke: Color = SelectedStroke,
) {
    val ownedCamera =
        cameraState
            ?: rememberSaveable(cameraResetKey, stateSaver = CameraStateSaver) {
                mutableStateOf(CameraState(centre = initialCentre, zoom = initialZoom))
            }
    var camera by ownedCamera
    var viewportWidthPx by remember { mutableStateOf(0f) }
    var viewportHeightPx by remember { mutableStateOf(0f) }

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(MAP_HEIGHT)
                .border(width = 1.5.dp, color = Color.Black)
                .onSizeChanged {
                    viewportWidthPx = it.width.toFloat()
                    viewportHeightPx = it.height.toFloat()
                    onViewportSizeChanged(it)
                }.pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoomChange, _ ->
                        camera =
                            camera.copy(
                                zoom = (camera.zoom * zoomChange).coerceIn(minZoom, maxZoom),
                                panOffsetPx =
                                    ScreenPoint(
                                        x = camera.panOffsetPx.x + pan.x,
                                        y = camera.panOffsetPx.y + pan.y,
                                    ),
                            )
                    }
                }.pointerInput(subRectangles) {
                    detectTapGestures { tapOffset ->
                        val world =
                            MapProjection.screenToWorld(
                                ScreenPoint(tapOffset.x, tapOffset.y),
                                camera,
                                viewportWidthPx,
                                viewportHeightPx,
                            )
                        onCodeSelected(MapHitTesting.hitTest(world, subRectangles))
                    }
                }.clearAndSetSemantics {
                    this.contentDescription = contentDescription
                    // Exposes the currently highlighted sub-rectangle so TalkBack announces the map's
                    // selection state (not just its static description), and so UI tests can assert the
                    // map's own selection state genuinely updates on a list-driven selection change.
                    this.stateDescription = selectedCode?.let { "Selected: $it" } ?: "No area selected"
                }.then(if (testTag.isBlank()) Modifier else Modifier.testTag(testTag)),
    ) {
        val viewport =
            GeoBoundingBox(
                minLat =
                    MapProjection
                        .screenToWorld(ScreenPoint(0f, size.height), camera, size.width, size.height)
                        .lat,
                maxLat = MapProjection.screenToWorld(ScreenPoint(0f, 0f), camera, size.width, size.height).lat,
                minLng = MapProjection.screenToWorld(ScreenPoint(0f, 0f), camera, size.width, size.height).lng,
                maxLng =
                    MapProjection
                        .screenToWorld(ScreenPoint(size.width, 0f), camera, size.width, size.height)
                        .lng,
            )

        landPolygons.forEach { land ->
            drawRings(
                land.rings,
                camera,
                size,
                fill = LandFill,
                stroke = LandOutline,
                strokeWidthPx = LAND_STROKE_WIDTH_PX,
            )
        }

        subRectangles.forEach { rectangle ->
            if (!rectangle.boundingBox.intersects(viewport)) return@forEach
            val selected = rectangle.subCode == selectedCode
            drawRings(
                rectangle.rings,
                camera,
                size,
                fill = if (selected) selectedFill else SubRectangleFill,
                stroke = if (selected) selectedStroke else SubRectangleStroke,
                strokeWidthPx = if (selected) SELECTED_STROKE_WIDTH_DP * GRID_STROKE_WIDTH_PX else GRID_STROKE_WIDTH_PX,
            )
        }

        if (camera.zoom >= LABEL_ZOOM_THRESHOLD) {
            subRectangles.forEach { rectangle ->
                if (!rectangle.seaOverlapping) return@forEach
                if (!rectangle.boundingBox.intersects(viewport)) return@forEach
                val screenPoint =
                    MapProjection.worldToScreen(rectangle.bboxCentroid, camera, size.width, size.height)
                if (rectangle.subCode == selectedCode) {
                    // The selected cell's code is shown on a solid, rounded-rect pill (white bold text on the
                    // same amber used for the cell's own selected fill/stroke) rather than plain text — see
                    // the confirmed reference screenshot. Only the selected cell gets a pill; every other
                    // (unselected) label stays plain text, unchanged.
                    drawPillLabel(rectangle.subCode, screenPoint, LABEL_TEXT_SIZE_SP, selectedStroke, Color.White)
                } else {
                    drawCentredText(rectangle.subCode, screenPoint, LABEL_TEXT_SIZE_SP, MmoColors.Text)
                }
            }
        }

        if (camera.zoom >= PORT_ZOOM_THRESHOLD) {
            MapViewportCulling.visiblePorts(ports, viewport).forEach { port ->
                val screenPoint = MapProjection.worldToScreen(port.location, camera, size.width, size.height)
                drawCircle(
                    color = PortColor,
                    radius = PORT_DOT_RADIUS_PX,
                    center = Offset(screenPoint.x, screenPoint.y),
                )
                drawCentredText(
                    port.name,
                    ScreenPoint(screenPoint.x, screenPoint.y + PORT_LABEL_OFFSET_PX),
                    PORT_LABEL_TEXT_SIZE_SP,
                    MmoColors.Text,
                )
            }
        }
    }
}

private const val PORT_LABEL_OFFSET_PX = 14f

/**
 * The nearby map's fixed viewport height — see MapZoomBounds's doc comment for how this and the screen's
 * available width are used as an approximate viewport size to derive zoom bounds ahead of first layout.
 */
internal val MAP_HEIGHT = 320.dp

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRings(
    rings: List<List<GeoPoint>>,
    camera: CameraState,
    canvasSize: Size,
    fill: Color,
    stroke: Color,
    strokeWidthPx: Float,
) {
    rings.forEach { ring ->
        if (ring.size < MIN_RING_POINTS) return@forEach
        val path = Path()
        ring.forEachIndexed { index, point ->
            val screenPoint = MapProjection.worldToScreen(point, camera, canvasSize.width, canvasSize.height)
            if (index == 0) path.moveTo(screenPoint.x, screenPoint.y) else path.lineTo(screenPoint.x, screenPoint.y)
        }
        path.close()
        drawPath(path, color = fill)
        drawPath(path, color = stroke, style = Stroke(width = strokeWidthPx))
    }
}

private const val MIN_RING_POINTS = 3

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCentredText(
    text: String,
    at: ScreenPoint,
    sizeSp: Int,
    color: Color,
) {
    drawContext.canvas.nativeCanvas.apply {
        val paint =
            android.graphics.Paint().apply {
                this.color = color.toArgbCompat()
                textSize = sizeSp.toFloat() * density
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
        drawText(text, at.x, at.y, paint)
    }
}

/**
 * Draws [text] centred at [at] on a solid, rounded-rect pill filled with [pillColor] — the selected cell's
 * code label, matching the confirmed reference screenshot's white-on-amber "pill" badge (as distinct from
 * every other, unselected cell's plain [drawCentredText] label).
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPillLabel(
    text: String,
    at: ScreenPoint,
    sizeSp: Int,
    pillColor: Color,
    textColor: Color,
) {
    drawContext.canvas.nativeCanvas.apply {
        val paint =
            android.graphics.Paint().apply {
                this.color = textColor.toArgbCompat()
                textSize = sizeSp.toFloat() * density
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
        val textBounds = android.graphics.Rect()
        paint.getTextBounds(text, 0, text.length, textBounds)
        val pillWidth = textBounds.width() + PILL_HORIZONTAL_PADDING_PX * 2
        val pillHeight = textBounds.height() + PILL_VERTICAL_PADDING_PX * 2
        val pillPaint =
            android.graphics.Paint().apply {
                this.color = pillColor.toArgbCompat()
                isAntiAlias = true
            }
        val pillRect =
            android.graphics.RectF(
                at.x - pillWidth / 2f,
                at.y - pillHeight / 2f,
                at.x + pillWidth / 2f,
                at.y + pillHeight / 2f,
            )
        drawRoundRect(pillRect, PILL_CORNER_RADIUS_PX, PILL_CORNER_RADIUS_PX, pillPaint)
        // android.graphics.Paint's baseline is the text's bottom, not its vertical centre, so nudge by half
        // the measured text height to genuinely centre the glyphs within the pill.
        drawText(text, at.x, at.y + textBounds.height() / 2f, paint)
    }
}

private const val PILL_HORIZONTAL_PADDING_PX = 12f
private const val PILL_VERTICAL_PADDING_PX = 6f
private const val PILL_CORNER_RADIUS_PX = 6f

private fun Color.toArgbCompat(): Int {
    val a = (alpha * MAX_BYTE).toInt()
    val r = (red * MAX_BYTE).toInt()
    val g = (green * MAX_BYTE).toInt()
    val b = (blue * MAX_BYTE).toInt()
    return (a shl SHIFT_24) or (r shl SHIFT_16) or (g shl SHIFT_8) or b
}

private const val MAX_BYTE = 255f
private const val SHIFT_24 = 24
private const val SHIFT_16 = 16
private const val SHIFT_8 = 8

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun StatisticalAreaMapCanvasPreview() {
    val subRectangle =
        StatisticalSubRectangleGeometry(
            subCode = "38E9",
            parentIcesName = "38E9",
            rings =
                listOf(
                    listOf(
                        GeoPoint(lat = 50.5, lng = 0.0),
                        GeoPoint(lat = 50.5, lng = 0.5),
                        GeoPoint(lat = 50.0, lng = 0.5),
                        GeoPoint(lat = 50.0, lng = 0.0),
                    ),
                ),
            bboxCentroid = GeoPoint(lat = 50.25, lng = 0.25),
            boundingBox = GeoBoundingBox(minLat = 50.0, maxLat = 50.5, minLng = 0.0, maxLng = 0.5),
            seaOverlapping = true,
        )
    val landPolygon =
        LandPolygon(
            rings =
                listOf(
                    listOf(
                        GeoPoint(lat = 50.5, lng = 0.0),
                        GeoPoint(lat = 50.5, lng = 0.15),
                        GeoPoint(lat = 50.0, lng = 0.1),
                        GeoPoint(lat = 50.0, lng = 0.0),
                    ),
                ),
        )
    StatisticalAreaMapCanvas(
        subRectangles = listOf(subRectangle),
        landPolygons = listOf(landPolygon),
        ports = listOf(MapPort(name = "Hastings", location = GeoPoint(lat = 50.85, lng = 0.57))),
        selectedCode = "38E9",
        onCodeSelected = {},
        initialCentre = GeoPoint(lat = 50.25, lng = 0.25),
        contentDescription = "Statistical sub-area map preview",
    )
}
