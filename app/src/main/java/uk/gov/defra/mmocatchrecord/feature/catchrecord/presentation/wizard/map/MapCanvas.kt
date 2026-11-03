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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
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
import kotlin.math.max
import kotlin.math.min

private const val MAP_BORDER_DP = 1.5f
private const val GRID_STROKE_DP = 1f
private const val SELECTED_STROKE_DP = 1.5f
private const val LAND_STROKE_DP = 0.6f
private const val PORT_DOT_RADIUS_DP = 3.5f
private const val LABEL_TEXT_SIZE_SP = 20f
private const val PORT_LABEL_TEXT_SIZE_SP = 14f
private const val PORT_LABEL_GAP_DP = 3f
private const val LABEL_BASELINE_OFFSET_DIVISOR = 3f
private const val LABEL_HALO_STROKE_DP = 3f
private const val SELECTED_CHIP_PAD_H_DP = 5f
private const val SELECTED_CHIP_PAD_V_DP = 2f
private const val SELECTED_CHIP_RADIUS_DP = 2f
private const val SELECTED_LAND_FILL_ALPHA = 0.6f
private const val TAP_SLOP_PX = 24f
private const val MAX_VISIBLE_LABELS = 60
private const val MAX_VISIBLE_PORTS = 120

/** Groups [camera] with its change callback â€” keeps [MapCanvas]'s parameter count within the kotlin:S107
 * limit instead of passing both separately. */
data class MapCameraState(
    val camera: MapCamera,
    val onCameraChange: (MapCamera) -> Unit,
)

/** Groups the selected statistical sub-rectangle code with its change callback â€” see [MapCameraState]. */
data class MapSelectionState(
    val selectedCode: String?,
    val onCodeSelected: (String?) -> Unit,
)

/**
 * The latest camera/selection/accessibility-action callbacks â€” read by the long-lived gesture coroutine
 * below on every recomposition rather than the values captured when `pointerInput` first started (see
 * [MapCanvas]'s single `rememberUpdatedState` call).
 */
private data class MapCanvasLatest(
    val cameraState: MapCameraState,
    val selection: MapSelectionState,
    val onChooseFromListRequested: () -> Unit,
)

/** Renders the offline statistical sub-rectangle map with `Canvas` only — no map SDK/tiles. Draw order and
 *  selection/TalkBack behaviour are documented in docs/development/offline-map.md. */
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
    val labelPaints = remember(density.density, density.fontScale) { MapLabelPaints(density) }
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
                    // Canvas does not clip by default â€” without this, land/grid/labels spill across the screen.
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
            drawMap(geometry, cameraState.camera, selection.selectedCode, density, labelPaints)
        }
    }
}

private const val MAP_ASPECT_RATIO_HEIGHT_OVER_WIDTH = 1.35f

/** Precomputed, dataset-derived geometry â€” built once per [MapDataset], never rebuilt on selection change. */
private class MapCanvasGeometry(
    val land: List<MultiPolygon>,
    val subRectangles: List<Pair<SerializableSubRectangle, MultiPolygon>>,
    val ports: List<SerializablePort>,
    val labelledCodes: Set<String>,
) {
    companion object {
        fun from(dataset: MapDataset): MapCanvasGeometry {
            val land = dataset.land.map { it.toMultiPolygon() }
            val landBounds = land.map { landBBox(it) }
            val subRectangles = dataset.subRectangles.map { it to it.geometry.toMultiPolygon() }
            // Labels only go on sea: skip cells whose label anchor falls on land.
            val labelledCodes =
                subRectangles
                    .filter { (subRect, _) ->
                        val anchor = subRect.centroid.toGeoPointLocal()
                        subRect.isSeaOverlapping &&
                            land.indices.none { landBounds[it].contains(anchor) && land[it].containsPoint(anchor) }
                    }.map { it.first.code }
                    .toSet()
            return MapCanvasGeometry(land, subRectangles, dedupePorts(dataset.ports), labelledCodes)
        }

        // The source data repeats many ports at identical coordinates, which would overpaint the same name.
        private fun dedupePorts(ports: List<SerializablePort>) = ports.distinctBy { it.name to it.point }

        private fun landBBox(multiPolygon: MultiPolygon): BBox =
            BBox.of(multiPolygon.polygons.flatMap { it.shell.points })
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

/** Camera plus the canvas pixel dimensions it's projecting into â€” bundled since nearly every draw/hit-test
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
    labelPaints: MapLabelPaints,
) {
    val projection = MapProjection(camera, size.width, size.height)
    val viewport = viewportBBox(projection.camera, projection.widthPx, projection.heightPx)

    val visibleSubRectangles =
        geometry.subRectangles.filter { (subRect, _) -> isSubRectangleInViewport(subRect, viewport) }
    val selected = visibleSubRectangles.firstOrNull { it.first.code == selectedCode }

    drawGridAndSelection(visibleSubRectangles, projection, selectedCode, density)
    drawLand(geometry, projection, density)
    drawSelectionOverLand(selected?.second, projection, density)
    drawSeaLabels(geometry, visibleSubRectangles, projection, labelPaints, selectedCode)
    drawPorts(geometry, projection, viewport, density, labelPaints)
    // Drawn last so a port dot or name can never obscure part of the selected cell's code.
    drawSelectedLabel(selected?.first, projection, labelPaints)
}

/** Label paints built once per density/font scale rather than per frame — pan and zoom redraw continuously. */
internal class MapLabelPaints(
    density: Density,
) {
    val textSizePx = with(density) { LABEL_TEXT_SIZE_SP.sp.toPx() }
    val baselineOffsetPx = textSizePx / LABEL_BASELINE_OFFSET_DIVISOR
    val fill = textPaint(textSizePx, android.graphics.Color.BLACK)

    // The selected code sits on a GOV.UK blue chip, so it is white: 5.2:1, where black would be 1.9:1.
    val selectedFill = textPaint(textSizePx, android.graphics.Color.WHITE)
    val selectedMetrics: Paint.FontMetrics = selectedFill.fontMetrics
    val chipPadHPx = with(density) { SELECTED_CHIP_PAD_H_DP.dp.toPx() }
    val chipPadVPx = with(density) { SELECTED_CHIP_PAD_V_DP.dp.toPx() }
    val chipRadiusPx = with(density) { SELECTED_CHIP_RADIUS_DP.dp.toPx() }

    private val portTextSizePx = with(density) { PORT_LABEL_TEXT_SIZE_SP.sp.toPx() }

    // Ports straddle the coastline, so their names need a halo to stay legible over both sea and land.
    val portHalo =
        textPaint(portTextSizePx, android.graphics.Color.WHITE).apply {
            style = Paint.Style.STROKE
            strokeWidth = with(density) { LABEL_HALO_STROKE_DP.dp.toPx() }
            strokeJoin = Paint.Join.ROUND
        }
    val portFill = textPaint(portTextSizePx, android.graphics.Color.BLACK)
    val portMetrics: Paint.FontMetrics = portFill.fontMetrics
    val portBaselineOffsetPx =
        with(density) { PORT_DOT_RADIUS_DP.dp.toPx() + PORT_LABEL_GAP_DP.dp.toPx() } - portFill.fontMetrics.ascent
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

/** Draws grid lines, then the selected cell's tint and border on top; land masks the grid, and
 *  [drawSelectionOverLand] restores the selected cell's highlight above it. */
private fun DrawScope.drawGridAndSelection(
    visibleSubRectangles: List<Pair<SerializableSubRectangle, MultiPolygon>>,
    projection: MapProjection,
    selectedCode: String?,
    density: Density,
) {
    visibleSubRectangles.forEach { (_, multiPolygon) ->
        drawMultiPolygon(
            multiPolygon,
            projection,
            MmoColors.MapGridLine,
            filled = false,
            strokeWidthDp = GRID_STROKE_DP,
            density = density,
        )
    }
    visibleSubRectangles.firstOrNull { it.first.code == selectedCode }?.let { (_, multiPolygon) ->
        drawMultiPolygon(multiPolygon, projection, MmoColors.MapSelectedFill, filled = true)
        drawMultiPolygon(
            multiPolygon,
            projection,
            MmoColors.MapSelectedStroke,
            filled = false,
            strokeWidthDp = SELECTED_STROKE_DP,
            density = density,
        )
    }
}

/** Re-draws the selected cell above land so its land portions stay highlighted too; the tint is translucent
 *  so the coastline still reads beneath it. */
private fun DrawScope.drawSelectionOverLand(
    multiPolygon: MultiPolygon?,
    projection: MapProjection,
    density: Density,
) {
    if (multiPolygon == null) return
    drawMultiPolygon(
        multiPolygon,
        projection,
        MmoColors.MapSelectedFill.copy(alpha = SELECTED_LAND_FILL_ALPHA),
        filled = true,
    )
    drawMultiPolygon(
        multiPolygon,
        projection,
        MmoColors.MapSelectedStroke,
        filled = false,
        strokeWidthDp = SELECTED_STROKE_DP,
        density = density,
    )
}

private fun DrawScope.drawSeaLabels(
    geometry: MapCanvasGeometry,
    visibleSubRectangles: List<Pair<SerializableSubRectangle, MultiPolygon>>,
    projection: MapProjection,
    labelPaints: MapLabelPaints,
    selectedCode: String?,
) {
    visibleSubRectangles
        .asSequence()
        .map { it.first }
        // The selected cell is excluded here because [drawSelectedLabel] always draws it, on its own blue chip.
        .filter { it.code in geometry.labelledCodes && it.code != selectedCode }
        .take(MAX_VISIBLE_LABELS)
        .forEach { subRect ->
            val screen =
                CameraMath.geoToScreen(
                    subRect.centroid.toGeoPointLocal(),
                    projection.camera,
                    projection.widthPx,
                    projection.heightPx,
                )
            drawContext.canvas.nativeCanvas.drawText(
                subRect.code,
                screen[0],
                screen[1] + labelPaints.baselineOffsetPx,
                labelPaints.fill,
            )
        }
}

/** The selected cell is always labelled — bypassing the sea-only [MapCanvasGeometry.labelledCodes] filter and
 *  the [MAX_VISIBLE_LABELS] cap — on a GOV.UK blue chip so the code reads whatever it is drawn over. */
private fun DrawScope.drawSelectedLabel(
    subRect: SerializableSubRectangle?,
    projection: MapProjection,
    labelPaints: MapLabelPaints,
) {
    if (subRect == null) return
    val screen =
        CameraMath.geoToScreen(
            subRect.centroid.toGeoPointLocal(),
            projection.camera,
            projection.widthPx,
            projection.heightPx,
        )
    val metrics = labelPaints.selectedMetrics
    val textWidthPx = labelPaints.selectedFill.measureText(subRect.code)
    val position =
        clampLabelIntoView(
            position = Offset(screen[0], screen[1] + labelPaints.baselineOffsetPx),
            textWidthPx = textWidthPx + labelPaints.chipPadHPx * 2f,
            ascentPx = metrics.ascent - labelPaints.chipPadVPx,
            descentPx = metrics.descent + labelPaints.chipPadVPx,
            bounds = selectedLabelBounds(subRect, projection),
            viewport = Rect(0f, 0f, projection.widthPx, projection.heightPx),
        )
    drawSelectedChip(position, textWidthPx, metrics, labelPaints)
    drawContext.canvas.nativeCanvas.drawText(subRect.code, position.x, position.y, labelPaints.selectedFill)
}

private fun DrawScope.drawSelectedChip(
    position: Offset,
    textWidthPx: Float,
    metrics: Paint.FontMetrics,
    labelPaints: MapLabelPaints,
) {
    val chipWidth = textWidthPx + labelPaints.chipPadHPx * 2f
    val chipHeight = metrics.descent - metrics.ascent + labelPaints.chipPadVPx * 2f
    drawRoundRect(
        color = MmoColors.MapSelectedStroke,
        topLeft = Offset(position.x - chipWidth / 2f, position.y + metrics.ascent - labelPaints.chipPadVPx),
        size = Size(chipWidth, chipHeight),
        cornerRadius = CornerRadius(labelPaints.chipRadiusPx),
    )
}

/** The cell's on-screen box intersected with the canvas — keeps the code chip inside its own cell and in view. */
private fun selectedLabelBounds(
    subRect: SerializableSubRectangle,
    projection: MapProjection,
): Rect {
    val topLeft =
        CameraMath.geoToScreen(
            GeoPoint(subRect.bbox.minLon, subRect.bbox.maxLat),
            projection.camera,
            projection.widthPx,
            projection.heightPx,
        )
    val bottomRight =
        CameraMath.geoToScreen(
            GeoPoint(subRect.bbox.maxLon, subRect.bbox.minLat),
            projection.camera,
            projection.widthPx,
            projection.heightPx,
        )
    return Rect(
        left = max(topLeft[0], 0f),
        top = max(topLeft[1], 0f),
        right = min(bottomRight[0], projection.widthPx),
        bottom = min(bottomRight[1], projection.heightPx),
    )
}

/** Keeps the whole label inside [bounds], falling back to [viewport] when it cannot fit in its own cell. */
internal fun clampLabelIntoView(
    position: Offset,
    textWidthPx: Float,
    ascentPx: Float,
    descentPx: Float,
    bounds: Rect,
    viewport: Rect,
): Offset {
    val halfWidth = textWidthPx / 2f
    return Offset(
        clampWithin(
            value = position.x,
            min = bounds.left + halfWidth,
            max = bounds.right - halfWidth,
            fallbackMin = viewport.left + halfWidth,
            fallbackMax = viewport.right - halfWidth,
        ),
        clampWithin(
            value = position.y,
            min = bounds.top - ascentPx,
            max = bounds.bottom - descentPx,
            fallbackMin = viewport.top - ascentPx,
            fallbackMax = viewport.bottom - descentPx,
        ),
    )
}

// Preferred range first, then the viewport, then centring — so an oversized label is never clipped off-screen.
private fun clampWithin(
    value: Float,
    min: Float,
    max: Float,
    fallbackMin: Float,
    fallbackMax: Float,
): Float =
    when {
        min <= max -> value.coerceIn(min, max)
        fallbackMin <= fallbackMax -> value.coerceIn(fallbackMin, fallbackMax)
        else -> (fallbackMin + fallbackMax) / 2f
    }

private fun DrawScope.drawPorts(
    geometry: MapCanvasGeometry,
    projection: MapProjection,
    viewport: BBox,
    density: Density,
    labelPaints: MapLabelPaints,
) {
    // Dots are always drawn; a name is skipped once it would overlap an already-placed one, so names never
    // collide even when several ports sit close together on screen.
    val placedLabelBounds = mutableListOf<Rect>()
    geometry.ports
        .asSequence()
        .filter { it.point.lon in viewport.minLon..viewport.maxLon && it.point.lat in viewport.minLat..viewport.maxLat }
        .take(MAX_VISIBLE_PORTS)
        .forEach { port ->
            val point = GeoPoint(port.point.lon, port.point.lat)
            val screen = CameraMath.geoToScreen(point, projection.camera, projection.widthPx, projection.heightPx)
            drawCircle(
                color = MmoColors.MapPortDot,
                radius = with(density) { PORT_DOT_RADIUS_DP.dp.toPx() },
                center = Offset(screen[0], screen[1]),
            )
            val baselineY = screen[1] + labelPaints.portBaselineOffsetPx
            val labelBounds = portLabelBounds(port.name, screen[0], baselineY, labelPaints)
            if (placedLabelBounds.none { it.overlapsRect(labelBounds) }) {
                placedLabelBounds += labelBounds
                val canvas = drawContext.canvas.nativeCanvas
                canvas.drawText(port.name, screen[0], baselineY, labelPaints.portHalo)
                canvas.drawText(port.name, screen[0], baselineY, labelPaints.portFill)
            }
        }
}

/** The on-screen box a port name's halo occupies, padded by the halo's stroke width so names are kept apart
 *  by their visible extent, not just their glyph outlines. */
internal fun portLabelBounds(
    name: String,
    centerX: Float,
    baselineY: Float,
    labelPaints: MapLabelPaints,
): Rect {
    val haloPx = labelPaints.portHalo.strokeWidth
    val halfWidth = labelPaints.portFill.measureText(name) / 2f + haloPx
    val metrics = labelPaints.portMetrics
    return Rect(
        left = centerX - halfWidth,
        top = baselineY + metrics.ascent - haloPx,
        right = centerX + halfWidth,
        bottom = baselineY + metrics.descent + haloPx,
    )
}

internal fun Rect.overlapsRect(other: Rect): Boolean =
    left < other.right && other.left < right && top < other.bottom && other.top < bottom

private fun SerializablePoint.toGeoPointLocal() = GeoPoint(lon, lat)

private fun DrawScope.drawMultiPolygon(
    multiPolygon: MultiPolygon,
    projection: MapProjection,
    color: Color,
    filled: Boolean,
    strokeWidthDp: Float = 1f,
    density: Density? = null,
) {
    val strokeWidthPx = strokeWidthPxFor(strokeWidthDp, density)
    multiPolygon.polygons.forEach { polygon ->
        val path = polygonPath(polygon, projection)
        if (filled) {
            drawPath(path, color = color)
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

private fun textPaint(
    sizePx: Float,
    color: Int,
) = Paint().apply {
    this.color = color
    textSize = sizePx
    isAntiAlias = true
    textAlign = Paint.Align.CENTER
}
