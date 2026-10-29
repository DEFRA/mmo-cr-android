@file:Suppress("detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardTestTheme
import uk.gov.defra.mmocatchrecord.mapdata.MapDataset
import uk.gov.defra.mmocatchrecord.mapdata.SerializableBBox
import uk.gov.defra.mmocatchrecord.mapdata.SerializableMultiPolygon
import uk.gov.defra.mmocatchrecord.mapdata.SerializablePoint
import uk.gov.defra.mmocatchrecord.mapdata.SerializablePolygon
import uk.gov.defra.mmocatchrecord.mapdata.SerializablePort
import uk.gov.defra.mmocatchrecord.mapdata.SerializableRing
import uk.gov.defra.mmocatchrecord.mapdata.SerializableSubRectangle

/** Exercises [MapCanvas]'s full `DrawScope` pipeline and gestures directly, which [MapScreenTests]'s
 *  single-sub-rectangle fixture cannot reach; native graphics let it assert on real rendered pixels. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapCanvasTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun square(
        minLon: Double,
        minLat: Double,
        maxLon: Double,
        maxLat: Double,
    ) = SerializableMultiPolygon(
        listOf(
            SerializablePolygon(
                SerializableRing(
                    listOf(
                        SerializablePoint(minLon, minLat),
                        SerializablePoint(maxLon, minLat),
                        SerializablePoint(maxLon, maxLat),
                        SerializablePoint(minLon, maxLat),
                        SerializablePoint(minLon, minLat),
                    ),
                ),
            ),
        ),
    )

    /**
     * Two side-by-side sea-overlapping sub-rectangles (so one can be selected while the other stays in its
     * unselected-label draw path), a land polygon, and a port — all comfortably inside the camera below.
     */
    private fun richDataset(): MapDataset {
        val rectA =
            SerializableSubRectangle(
                code = "AAA01",
                icesName = "Rectangle A",
                areaKm2 = 1.0,
                bbox = SerializableBBox(minLon = -20.0, minLat = 30.0, maxLon = 0.0, maxLat = 75.0),
                centroid = SerializablePoint(-10.0, 55.0),
                geometry = square(-20.0, 30.0, 0.0, 75.0),
                isSeaOverlapping = true,
            )
        val rectB =
            SerializableSubRectangle(
                code = "BBB02",
                icesName = "Rectangle B",
                areaKm2 = 1.0,
                bbox = SerializableBBox(minLon = 0.0, minLat = 30.0, maxLon = 20.0, maxLat = 75.0),
                centroid = SerializablePoint(10.0, 55.0),
                geometry = square(0.0, 30.0, 20.0, 75.0),
                isSeaOverlapping = true,
            )
        val rectOnLand =
            SerializableSubRectangle(
                code = "CCC03",
                icesName = "Rectangle C",
                areaKm2 = 1.0,
                bbox = SerializableBBox(minLon = -19.0, minLat = 60.0, maxLon = -15.0, maxLat = 65.0),
                centroid = SerializablePoint(-17.0, 62.5),
                geometry = square(-19.0, 60.0, -15.0, 65.0),
                isSeaOverlapping = true,
            )
        return MapDataset(
            formatVersion = MapDataset.CURRENT_FORMAT_VERSION,
            land = listOf(square(-19.0, 60.0, -15.0, 65.0)),
            subRectangles = listOf(rectA, rectB, rectOnLand),
            ports =
                listOf(
                    SerializablePort(portCode = "PORT1", name = "Test Port", point = SerializablePoint(-10.0, 55.0)),
                ),
        )
    }

    /** Wide enough to show both [richDataset] sub-rectangles (lon -20..20) across the canvas at once. */
    private val wideCamera = MapCamera(centerLon = 0.0, centerLat = 55.0, visibleWidthMetres = 8_000_000.0)

    @Test
    fun tappingASeaOverlappingRectangleSelectsItAndDrawsGridLandLabelsAndPorts() {
        var selectedCode by mutableStateOf<String?>(null)
        var camera by mutableStateOf(wideCamera)
        composeTestRule.setContent {
            WizardTestTheme {
                MapCanvas(
                    dataset = richDataset(),
                    cameraState = MapCameraState(camera) { camera = it },
                    selection = MapSelectionState(selectedCode) { selectedCode = it },
                )
            }
        }

        // Force an initial draw pass (no selection yet) before anything is tapped.
        composeTestRule.onNodeWithTag(MapScreenTestTags.MAP).captureToImage()

        // Rectangle A occupies the left half of the canvas.
        composeTestRule
            .onNodeWithTag(MapScreenTestTags.MAP)
            .performTouchInput { click(Offset(x = width * 0.25f, y = height / 2f)) }

        assertEquals("AAA01", selectedCode)

        // Re-draw now that one rectangle is selected and the other is an unselected, labelled neighbour.
        composeTestRule.onNodeWithTag(MapScreenTestTags.MAP).captureToImage()
    }

    @Test
    fun tappingOutsideEverySeaOverlappingRectangleClearsSelection() {
        var selectedCode by mutableStateOf<String?>("AAA01")
        val camera = wideCamera
        composeTestRule.setContent {
            WizardTestTheme {
                MapCanvas(
                    dataset = richDataset(),
                    cameraState = MapCameraState(camera) {},
                    selection = MapSelectionState(selectedCode) { selectedCode = it },
                )
            }
        }

        // The very top-left corner of this wide a viewport falls well outside both sub-rectangles' bbox.
        composeTestRule
            .onNodeWithTag(MapScreenTestTags.MAP)
            .performTouchInput { click(Offset(x = 0f, y = 0f)) }

        assertNull(selectedCode)
    }

    @Test
    fun dragGestureBeyondTapSlopPansTheCameraInsteadOfSelecting() {
        var selectedCode by mutableStateOf<String?>(null)
        var camera by mutableStateOf(wideCamera)
        composeTestRule.setContent {
            WizardTestTheme {
                MapCanvas(
                    dataset = richDataset(),
                    cameraState = MapCameraState(camera) { camera = it },
                    selection = MapSelectionState(selectedCode) { selectedCode = it },
                )
            }
        }

        composeTestRule.onNodeWithTag(MapScreenTestTags.MAP).performTouchInput {
            swipe(start = center, end = Offset(center.x + 120f, center.y), durationMillis = 200)
        }

        assertNull(selectedCode)
        assertNotEquals(wideCamera.centerLon, camera.centerLon, 1e-9)
    }

    /** A single sub-rectangle sitting entirely on land, centred in [wideCamera] — the real-world case where a
     *  cell's centroid falls on the coastline and so is excluded from the sea-only label set. */
    private fun landCoveredDataset(): MapDataset {
        val landSquare = square(-20.0, 30.0, 20.0, 75.0)
        return MapDataset(
            formatVersion = MapDataset.CURRENT_FORMAT_VERSION,
            land = listOf(landSquare),
            subRectangles =
                listOf(
                    SerializableSubRectangle(
                        code = "LLL01",
                        icesName = "Rectangle L",
                        areaKm2 = 1.0,
                        bbox = SerializableBBox(minLon = -20.0, minLat = 30.0, maxLon = 20.0, maxLat = 75.0),
                        centroid = SerializablePoint(0.0, 55.0),
                        geometry = landSquare,
                        isSeaOverlapping = true,
                    ),
                ),
            ports = emptyList(),
        )
    }

    /** True if any pixel within [LABEL_SAMPLE_HALF_BOX] of the canvas centre, where the selected cell's label
     *  is drawn, matches [predicate]. */
    private fun hasPixelNearCentre(
        pixels: PixelMap,
        predicate: (Color) -> Boolean,
    ): Boolean =
        hasPixelIn(
            pixels,
            xRange = (pixels.width / 2 - LABEL_SAMPLE_HALF_BOX)..(pixels.width / 2 + LABEL_SAMPLE_HALF_BOX),
            yRange = (pixels.height / 2 - LABEL_SAMPLE_HALF_BOX)..(pixels.height / 2 + LABEL_SAMPLE_HALF_BOX),
            predicate = predicate,
        )

    private fun hasPixelIn(
        pixels: PixelMap,
        xRange: IntRange,
        yRange: IntRange,
        predicate: (Color) -> Boolean,
    ): Boolean {
        for (y in yRange) {
            for (x in xRange) {
                if (x !in 0 until pixels.width || y !in 0 until pixels.height) continue
                if (predicate(pixels[x, y])) return true
            }
        }
        return false
    }

    // Land is #0B4143, whose green channel is well above this threshold, so only text glyphs match.
    private fun isGlyph(pixel: Color): Boolean =
        pixel.alpha > 0.9f && pixel.red < 0.2f && pixel.green < 0.2f && pixel.blue < 0.2f

    // Nothing else near the centre of an all-land cell is GOV.UK blue, so this can only be the selected code chip.
    private fun hasChipNearCentre(pixels: PixelMap) =
        hasPixelNearCentre(pixels) { it.red < 0.25f && it.green > 0.35f && it.green < 0.55f && it.blue > 0.6f }

    // The selected code is white on that blue chip, and nothing else drawn over land is white.
    private fun hasWhiteNearCentre(pixels: PixelMap) =
        hasPixelNearCentre(pixels) { it.red > 0.9f && it.green > 0.9f && it.blue > 0.9f }

    private fun hasGlyphNearCentre(pixels: PixelMap) = hasPixelNearCentre(pixels, ::isGlyph)

    private fun renderLandCovered(selectedCode: String?): PixelMap {
        composeTestRule.setContent {
            WizardTestTheme {
                MapCanvas(
                    dataset = landCoveredDataset(),
                    cameraState = MapCameraState(wideCamera) {},
                    selection = MapSelectionState(selectedCode) {},
                )
            }
        }
        return composeTestRule
            .onNodeWithTag(MapScreenTestTags.MAP)
            .captureToImage()
            .toPixelMap()
    }

    /** A quarter-height below centre: still well inside the all-land cell, but clear of its centroid label. */
    private fun fillSample(pixels: PixelMap) = pixels[pixels.width / 2, pixels.height / 2 + pixels.height / 4]

    @Test
    fun anUnselectedCellCoveringLandRendersAsPlainLandAndIsNotLabelled() {
        val pixels = renderLandCovered(selectedCode = null)

        val fill = fillSample(pixels)
        assertEquals("The canvas must actually rasterise, was $fill", 1f, fill.alpha, 0.01f)
        assertTrue("Expected plain land, was $fill", fill.blue < 0.4f)
        assertFalse("An unselected land-centroid cell must not be labelled", hasGlyphNearCentre(pixels))
        assertFalse("No code chip should be drawn without a selection", hasChipNearCentre(pixels))
        assertFalse("No white label text should be drawn without a selection", hasWhiteNearCentre(pixels))
    }

    @Test
    fun aSelectedCellCoveringLandIsTintedAcrossTheLandAndIsAlwaysLabelled() {
        val pixels = renderLandCovered(selectedCode = "LLL01")

        val fill = fillSample(pixels)
        assertTrue("Expected the land portion to be tinted, was $fill", fill.blue > 0.5f)
        assertTrue("The selected code must sit on a GOV.UK blue chip", hasChipNearCentre(pixels))
        assertTrue("A selected cell must show its code even where it covers land", hasWhiteNearCentre(pixels))
    }

    /** A port at the camera centre on open sea, with the only sub-rectangle's label pushed far left so it
     *  cannot be mistaken for the port's own name. */
    private fun portDataset(): MapDataset =
        MapDataset(
            formatVersion = MapDataset.CURRENT_FORMAT_VERSION,
            land = emptyList(),
            subRectangles =
                listOf(
                    SerializableSubRectangle(
                        code = "PPP01",
                        icesName = "Rectangle P",
                        areaKm2 = 1.0,
                        bbox = SerializableBBox(minLon = -20.0, minLat = 30.0, maxLon = 20.0, maxLat = 75.0),
                        centroid = SerializablePoint(-15.0, 55.0),
                        geometry = square(-20.0, 30.0, 20.0, 75.0),
                        isSeaOverlapping = true,
                    ),
                ),
            ports =
                listOf(
                    SerializablePort(portCode = "PORT1", name = "Test Port", point = SerializablePoint(0.0, 55.0)),
                ),
        )

    @Test
    fun aVisiblePortIsNamedOnTheMapNotJustMarkedWithADot() {
        composeTestRule.setContent {
            WizardTestTheme {
                MapCanvas(
                    dataset = portDataset(),
                    cameraState = MapCameraState(wideCamera) {},
                    selection = MapSelectionState(null) {},
                )
            }
        }
        val pixels =
            composeTestRule
                .onNodeWithTag(MapScreenTestTags.MAP)
                .captureToImage()
                .toPixelMap()

        val centreX = pixels.width / 2
        val centreY = pixels.height / 2
        val named =
            hasPixelIn(
                pixels,
                xRange = (centreX - PORT_NAME_HALF_WIDTH)..(centreX + PORT_NAME_HALF_WIDTH),
                yRange = (centreY + PORT_NAME_BAND_TOP)..(centreY + PORT_NAME_BAND_BOTTOM),
                predicate = ::isGlyph,
            )
        assertTrue("A port must render its name beneath its dot", named)
    }

    private fun clamp(
        x: Float,
        y: Float,
        textWidthPx: Float = LABEL_WIDTH,
        bounds: Rect = Rect(0f, 0f, CLAMP_CANVAS_WIDTH, CLAMP_CANVAS_HEIGHT),
        viewport: Rect = Rect(0f, 0f, CLAMP_CANVAS_WIDTH, CLAMP_CANVAS_HEIGHT),
    ): Offset =
        clampLabelIntoView(
            position = Offset(x, y),
            textWidthPx = textWidthPx,
            ascentPx = LABEL_ASCENT,
            descentPx = LABEL_DESCENT,
            bounds = bounds,
            viewport = viewport,
        )

    @Test
    fun aLabelComfortablyInsideTheCanvasIsLeftExactlyWhereItIs() {
        val clamped = clamp(x = 100f, y = 150f)

        assertEquals(100f, clamped.x, 0.01f)
        assertEquals(150f, clamped.y, 0.01f)
    }

    @Test
    fun aLabelOverflowingEitherHorizontalEdgeIsClampedSoTheWholeCodeStaysVisible() {
        assertEquals(CLAMP_CANVAS_WIDTH - LABEL_WIDTH / 2f, clamp(x = 10_000f, y = 150f).x, 0.01f)
        assertEquals(LABEL_WIDTH / 2f, clamp(x = -10_000f, y = 150f).x, 0.01f)
    }

    @Test
    fun aLabelOverflowingEitherVerticalEdgeIsClampedToKeepItsAscenderAndDescenderVisible() {
        assertEquals(-LABEL_ASCENT, clamp(x = 100f, y = -10_000f).y, 0.01f)
        assertEquals(CLAMP_CANVAS_HEIGHT - LABEL_DESCENT, clamp(x = 100f, y = 10_000f).y, 0.01f)
    }

    @Test
    fun aLabelWiderThanTheCanvasIsCentredRatherThanThrowing() {
        val oversized = CLAMP_CANVAS_WIDTH * 4f
        val clamped = clamp(x = 10f, y = 150f, textWidthPx = oversized)

        assertEquals(CLAMP_CANVAS_WIDTH / 2f, clamped.x, 0.01f)
    }

    @Test
    fun aLabelIsHeldInsideTheSelectedCellSoItNeverStraysOntoUntintedLand() {
        val cell = Rect(left = 20f, top = 40f, right = 120f, bottom = 180f)

        assertEquals(cell.right - LABEL_WIDTH / 2f, clamp(x = 10_000f, y = 100f, bounds = cell).x, 0.01f)
        assertEquals(cell.left + LABEL_WIDTH / 2f, clamp(x = -10_000f, y = 100f, bounds = cell).x, 0.01f)
        assertEquals(cell.top - LABEL_ASCENT, clamp(x = 70f, y = -10_000f, bounds = cell).y, 0.01f)
        assertEquals(cell.bottom - LABEL_DESCENT, clamp(x = 70f, y = 10_000f, bounds = cell).y, 0.01f)
    }

    @Test
    fun aLabelTooBigForItsCellFallsBackToTheViewportRatherThanBeingClippedOffScreen() {
        val slivercell = Rect(left = 0f, top = 0f, right = 2f, bottom = 2f)

        val clamped = clamp(x = 0f, y = 0f, bounds = slivercell)

        assertEquals(LABEL_WIDTH / 2f, clamped.x, 0.01f)
        assertEquals(-LABEL_ASCENT, clamped.y, 0.01f)
    }

    private companion object {
        const val LABEL_SAMPLE_HALF_BOX = 24
        const val PORT_NAME_BAND_TOP = 8
        const val PORT_NAME_BAND_BOTTOM = 60
        const val PORT_NAME_HALF_WIDTH = 70
        const val CLAMP_CANVAS_WIDTH = 200f
        const val CLAMP_CANVAS_HEIGHT = 300f
        const val LABEL_WIDTH = 40f

        // Android FontMetrics reports ascent above the baseline as a negative value.
        const val LABEL_ASCENT = -11f
        const val LABEL_DESCENT = 4f
    }
}
