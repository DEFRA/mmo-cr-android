@file:Suppress("detekt.LongMethod", "detekt.TooManyFunctions")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.map

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.MmoColors
import uk.gov.defra.mmocatchrecord.mapdata.BBox
import uk.gov.defra.mmocatchrecord.mapdata.GeoPoint
import uk.gov.defra.mmocatchrecord.mapdata.GeoPolygon
import uk.gov.defra.mmocatchrecord.mapdata.MapDataset
import uk.gov.defra.mmocatchrecord.mapdata.MultiPolygon
import uk.gov.defra.mmocatchrecord.mapdata.Ring
import uk.gov.defra.mmocatchrecord.mapdata.SerializablePoint
import uk.gov.defra.mmocatchrecord.mapdata.SerializablePort
import uk.gov.defra.mmocatchrecord.mapdata.SerializableSubRectangle
import uk.gov.defra.mmocatchrecord.mapdata.toMultiPolygon
import kotlin.math.hypot

private const val MAP_BORDER_DP = 1.5f
private const val GRID_STROKE_DP = 1f
private const val GRID_FILL_ALPHA = 0.06f
private const val SELECTED_FILL_ALPHA = 0.35f
private const val SELECTED_STROKE_MULTIPLIER = 3f
private const val LAND_STROKE_DP = 0.6f
private const val PORT_DOT_RADIUS_DP = 3.5f
private const val LABEL_TEXT_SIZE_SP = 12f
private const val PORT_LABEL_TEXT_SIZE_SP = 11f
private const val TAP_SLOP_PX = 24f
private const val MAX_VISIBLE_LABELS = 60
private const val MAX_VISIBLE_PORTS = 120
private const val PORT_LABEL_OFFSET_DP = 6f
private const val PILL_PADDING_DP = 6f
private const val HALO_STROKE_WIDTH_DP = 3f
private const val PILL_TEXT_BASELINE_OFFSET_DIVISOR = 3f

/** Groups [camera] with its change callback — keeps [MapCanvas]'s parameter count within the kotlin:S107
 * limit instead of passing both separately. */
data class MapCameraState(
    val camera: MapCamera,
    val onCameraChange: (MapCamera) -> Unit,
)

/** Groups the selected statistical sub-rectangle code with its change callback — see [MapCameraState]. */
data class MapSelectionState(
    val selectedCode: String?,
    val onCodeSelected: (String?) -> Unit,
)

/**
 * The latest camera/selection/accessibility-action callbacks — read by the long-lived gesture coroutine
 * below on every recomposition rather than the values captured when `pointerInput` first started (see
 * [MapCanvas]'s single `rememberUpdatedState` call).
 */
private data class MapCanvasLatest(
    val cameraState: MapCameraState,
    val selection: MapSelectionState,
    val onChooseFromListRequested: () -> Unit,
)

/**
 * Renders the offline fisheries statistical sub-rectangle map (see docs/development/offline-map.md) using
 * `Canvas` only — no map SDK/tiles. Draw order bottom-to-top: sea background, land, sub-rectangle grid,
 * selected highlight, labels, ports. A single Compose semantics node describes the whole map for TalkBack;
 * selection changes are announced via a polite live region.
 */
@Composable
@Suppress("FunctionNaming")
fun MapCanvas(
    dataset: MapDataset,
    cameraState: MapCameraState,
    selection: MapSelectionState,
    modifier: Modifier = Modifier,
    testTag: String = MapScreenTestTags.MAP,
    onChooseFromListRequested: () -> Unit = {},
) {
    val density = LocalDensity.current
    val geometry = remember(dataset) { MapCanvasGeometry.from(dataset) }
    // The gesture coroutine outlives recompositions; read the latest camera/callbacks rather than the
    // values captured when pointerInput first started.
    val latest by rememberUpdatedState(MapCanvasLatest(cameraState, selection, onChooseFromListRequested))

    val selectionClause =
        selection.selectedCode?.let { stringResource(R.string.gear_stat_rectangle_selected_area, it) }
            ?: stringResource(R.string.gear_stat_rectangle_selected_area_none)
    val mapContentDescription = stringResource(R.string.gear_stat_rectangle_map_content_description, selectionClause)
    val chooseFromListActionLabel = stringResource(R.string.gear_stat_rectangle_map_choose_from_list_action)

    Box(
        modifier =
            modifier
                .testTag(testTag)
                .semantics {
                    contentDescription = mapContentDescription
                    liveRegion = LiveRegionMode.Polite
                    customActions =
                        listOf(
                            CustomAccessibilityAction(
                                label = chooseFromListActionLabel,
                                action = {
                                    latest.onChooseFromListRequested()
                                    true
                                },
                            ),
                        )
                },
    ) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f / MAP_ASPECT_RATIO_HEIGHT_OVER_WIDTH)
                    // Canvas does not clip by default — without this, land/grid/labels spill across the screen.
                    .clipToBounds()
                    .border(MAP_BORDER_DP.dp, Color.Black)
                    .background(Color.White)
                    .pointerInput(geometry) {
                        handleMapGestures(
                            onTap = { x, y ->
                                val projection =
                                    MapProjection(
                                        latest.cameraState.camera,
                                        size.width.toFloat(),
                                        size.height.toFloat(),
                                    )
                                latest.selection.onCodeSelected(hitTest(geometry, projection, x, y))
                            },
                            onTransform = { pan, zoom ->
                                val panned =
                                    CameraMath.pan(latest.cameraState.camera, pan.x, pan.y, size.width.toFloat())
                                latest.cameraState.onCameraChange(CameraMath.zoom(panned, zoom))
                            },
                        )
                    },
        ) {
            drawMap(geometry, cameraState.camera, selection.selectedCode, density)
        }
    }
}

private const val MAP_ASPECT_RATIO_HEIGHT_OVER_WIDTH = 1.35f

/** Precomputed, dataset-derived geometry — built once per [MapDataset], never rebuilt on selection change. */
private class MapCanvasGeometry(
    val land: List<MultiPolygon>,
    val subRectangles: List<Pair<SerializableSubRectangle, MultiPolygon>>,
    val ports: List<SerializablePort>,
) {
    companion object {
        fun from(dataset: MapDataset): MapCanvasGeometry =
            MapCanvasGeometry(
                land = dataset.land.map { it.toMultiPolygon() },
                subRectangles = dataset.subRectangles.map { it to it.geometry.toMultiPolygon() },
                ports = dataset.ports,
            )
    }
}

private suspend fun PointerInputScope.handleMapGestures(
    onTap: (Float, Float) -> Unit,
    onTransform: (Offset, Float) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown()
        var pointerCount: Int
        var totalPan = Offset.Zero
        var moved = false
        do {
            val event = awaitPointerEvent()
            pointerCount = event.changes.size
            val zoom = event.calculateZoom()
            val pan = event.calculatePan()
            if (pan != Offset.Zero || zoom != 1f) {
                totalPan += pan
                if (hypot(totalPan.x, totalPan.y) > TAP_SLOP_PX || zoom != 1f) {
                    moved = true
                    onTransform(pan, zoom)
                    event.changes.forEach { it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
        if (!moved && pointerCount == 1) {
            onTap(down.position.x, down.position.y)
        }
    }
}

/** Camera plus the canvas pixel dimensions it's projecting into — bundled since nearly every draw/hit-test
 * helper below needs all three together (see kotlin:S107 parameter-count limit). */
private data class MapProjection(
    val camera: MapCamera,
    val widthPx: Float,
    val heightPx: Float,
)

private fun hitTest(
    geometry: MapCanvasGeometry,
    projection: MapProjection,
    x: Float,
    y: Float,
): String? {
    val geoPoint = CameraMath.screenToGeo(x, y, projection.camera, projection.widthPx, projection.heightPx)
    val hit =
        geometry.subRectangles.firstOrNull { (subRect, multiPolygon) ->
            subRect.isSeaOverlapping &&
                subRect.bbox.let {
                    it.minLon <= geoPoint.lon &&
                        geoPoint.lon <= it.maxLon &&
                        it.minLat <= geoPoint.lat &&
                        geoPoint.lat <= it.maxLat
                } &&
                multiPolygon.containsPoint(geoPoint)
        }
    return hit?.first?.code
}

private fun DrawScope.drawMap(
    geometry: MapCanvasGeometry,
    camera: MapCamera,
    selectedCode: String?,
    density: Density,
) {
    val projection = MapProjection(camera, size.width, size.height)
    val viewport = viewportBBox(projection.camera, projection.widthPx, projection.heightPx)

    drawLand(geometry, projection, density)
    drawGridAndSelection(geometry, projection, viewport, selectedCode, density)
    drawPorts(geometry, projection, viewport, density)
}

private fun DrawScope.drawLand(
    geometry: MapCanvasGeometry,
    projection: MapProjection,
    density: Density,
) {
    geometry.land.forEach { multiPolygon ->
        drawMultiPolygon(multiPolygon, projection, MmoColors.MapLand, filled = true)
        drawMultiPolygon(
            multiPolygon,
            projection,
            Color.Black,
            filled = false,
            strokeWidthDp = LAND_STROKE_DP,
            density = density,
        )
    }
}

/** Draws every viewport-culled sub-rectangle's grid line/fill, plus the amber selected highlight and its
 * pill label — see requirement #6's draw order (grid, then selected, then labels). */
private fun DrawScope.drawGridAndSelection(
    geometry: MapCanvasGeometry,
    projection: MapProjection,
    viewport: BBox,
    selectedCode: String?,
    density: Density,
) {
    var visibleLabels = 0
    val visibleSubRectangles =
        geometry.subRectangles.filter { (subRect, _) ->
            isSubRectangleInViewport(subRect, viewport)
        }
    visibleSubRectangles.forEach { (subRect, multiPolygon) ->
        val isSelected = subRect.code == selectedCode
        val strokeWidthDp = if (isSelected) GRID_STROKE_DP * SELECTED_STROKE_MULTIPLIER else GRID_STROKE_DP
        val colour = if (isSelected) MmoColors.MapSelectedFill else MmoColors.MapGridLine
        val fillAlpha = if (isSelected) SELECTED_FILL_ALPHA else GRID_FILL_ALPHA
        drawMultiPolygon(multiPolygon, projection, colour, filled = true, fillAlpha = fillAlpha)
        drawMultiPolygon(
            multiPolygon,
            projection,
            colour,
            filled = false,
            strokeWidthDp = strokeWidthDp,
            density = density,
        )

        if (!isSelected && subRect.isSeaOverlapping && visibleLabels < MAX_VISIBLE_LABELS) {
            drawSubRectangleLabel(subRect, projection, density, isSelected = false)
            visibleLabels++
        }
    }
    // Selected label drawn last (on top) as the amber "pill".
    selectedCode?.let { code ->
        geometry.subRectangles.firstOrNull { it.first.code == code }?.let { (subRect, _) ->
            drawSubRectangleLabel(subRect, projection, density, isSelected = true)
        }
    }
}

private fun DrawScope.drawPorts(
    geometry: MapCanvasGeometry,
    projection: MapProjection,
    viewport: BBox,
    density: Density,
) {
    geometry.ports
        .asSequence()
        .map { it to GeoPoint(it.point.lon, it.point.lat) }
        .filter { (_, point) -> viewport.contains(point) }
        .take(MAX_VISIBLE_PORTS)
        .forEach { (port, point) ->
            val screen = CameraMath.geoToScreen(point, projection.camera, projection.widthPx, projection.heightPx)
            drawCircle(
                color = MmoColors.MapPortDot,
                radius = with(density) { PORT_DOT_RADIUS_DP.dp.toPx() },
                center = Offset(screen[0], screen[1]),
            )
            drawHaloText(
                port.name,
                screen[0] + with(density) { PORT_LABEL_OFFSET_DP.dp.toPx() },
                screen[1],
                PORT_LABEL_TEXT_SIZE_SP,
                android.graphics.Color.BLACK,
                density,
            )
        }
}

private fun SerializablePoint.toGeoPointLocal() = GeoPoint(lon, lat)

private fun DrawScope.drawMultiPolygon(
    multiPolygon: MultiPolygon,
    projection: MapProjection,
    color: Color,
    filled: Boolean,
    fillAlpha: Float = 1f,
    strokeWidthDp: Float = 1f,
    density: Density? = null,
) {
    val strokeWidthPx = strokeWidthPxFor(strokeWidthDp, density)
    multiPolygon.polygons.forEach { polygon ->
        val path = polygonPath(polygon, projection)
        if (filled) {
            drawPath(path, color = color.copy(alpha = fillAlpha))
        } else {
            drawPath(path, color = color, style = Stroke(width = strokeWidthPx))
        }
    }
}

private fun strokeWidthPxFor(
    strokeWidthDp: Float,
    density: Density?,
): Float = density?.let { with(it) { strokeWidthDp.dp.toPx() } } ?: strokeWidthDp

private fun polygonPath(
    polygon: GeoPolygon,
    projection: MapProjection,
): Path {
    val path = Path()
    appendRing(path, polygon.shell, projection)
    polygon.holes.forEach { appendRing(path, it, projection) }
    path.fillType = PathFillType.EvenOdd
    return path
}

private fun appendRing(
    path: Path,
    ring: Ring,
    projection: MapProjection,
) {
    ring.points.forEachIndexed { index, point ->
        val screen = CameraMath.geoToScreen(point, projection.camera, projection.widthPx, projection.heightPx)
        if (index == 0) path.moveTo(screen[0], screen[1]) else path.lineTo(screen[0], screen[1])
    }
    path.close()
}

private fun DrawScope.drawSubRectangleLabel(
    subRect: SerializableSubRectangle,
    projection: MapProjection,
    density: Density,
    isSelected: Boolean,
) {
    val screen =
        CameraMath.geoToScreen(
            subRect.centroid.toGeoPointLocal(),
            projection.camera,
            projection.widthPx,
            projection.heightPx,
        )
    if (isSelected) {
        drawSelectedPillLabel(subRect.code, screen[0], screen[1], density)
    } else {
        drawHaloText(subRect.code, screen[0], screen[1], LABEL_TEXT_SIZE_SP, MmoColors.MapGridLine.toArgb(), density)
    }
}

private fun textPaint(
    sizePx: Float,
    color: Int,
    bold: Boolean = false,
) = Paint().apply {
    this.color = color
    textSize = sizePx
    isFakeBoldText = bold
    isAntiAlias = true
    textAlign = Paint.Align.CENTER
}

private fun DrawScope.drawSelectedPillLabel(
    code: String,
    x: Float,
    y: Float,
    density: Density,
) {
    val textSizePx = with(density) { LABEL_TEXT_SIZE_SP.sp.toPx() }
    val paint = textPaint(textSizePx, android.graphics.Color.WHITE, bold = true)
    val pillPaint =
        Paint().apply {
            color = MmoColors.MapSelectedFill.toArgb()
            isAntiAlias = true
        }
    val textWidth = paint.measureText(code)
    val padding = with(density) { PILL_PADDING_DP.dp.toPx() }

    drawContext.canvas.nativeCanvas.apply {
        drawRoundRect(
            x - textWidth / 2f - padding,
            y - textSizePx / 2f - padding / 2f,
            x + textWidth / 2f + padding,
            y + textSizePx / 2f + padding / 2f,
            padding,
            padding,
            pillPaint,
        )
        drawText(code, x, y + textSizePx / PILL_TEXT_BASELINE_OFFSET_DIVISOR, paint)
    }
}

private fun DrawScope.drawHaloText(
    text: String,
    x: Float,
    y: Float,
    sizeSp: Float,
    color: Int,
    density: Density,
) {
    val textSizePx = with(density) { sizeSp.sp.toPx() }
    val strokeWidthPx = with(density) { HALO_STROKE_WIDTH_DP.dp.toPx() }
    val halo =
        textPaint(textSizePx, android.graphics.Color.WHITE).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx
        }
    val fill = textPaint(textSizePx, color)

    drawContext.canvas.nativeCanvas.apply {
        drawText(text, x, y, halo)
        drawText(text, x, y, fill)
    }
}
