@file:Suppress("detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardTestTheme
import uk.gov.defra.mmocatchrecord.mapdata.MapDataset
import uk.gov.defra.mmocatchrecord.mapdata.SerializableBBox
import uk.gov.defra.mmocatchrecord.mapdata.SerializableMultiPolygon
import uk.gov.defra.mmocatchrecord.mapdata.SerializablePoint
import uk.gov.defra.mmocatchrecord.mapdata.SerializablePolygon
import uk.gov.defra.mmocatchrecord.mapdata.SerializablePort
import uk.gov.defra.mmocatchrecord.mapdata.SerializableRing
import uk.gov.defra.mmocatchrecord.mapdata.SerializableSubRectangle

/**
 * Exercises [MapCanvas] directly (rather than only through [MapScreen]) with a dataset rich enough to drive
 * its entire `DrawScope` rendering pipeline (land, unselected grid labels, the selected "pill" label, and
 * ports) plus its pan gesture handling and its "tap misses everything" hit-test path — none of which
 * [MapScreenTests]'s single-sub-rectangle fixture reaches. `captureToImage()` is used purely to force
 * Robolectric to actually run the `Canvas` draw lambda (semantics-only assertions like `assertIsDisplayed`
 * do not) — see this PR's coverage notes; it is not asserting on pixel content.
 */
@RunWith(RobolectricTestRunner::class)
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
        return MapDataset(
            formatVersion = MapDataset.CURRENT_FORMAT_VERSION,
            land = listOf(square(-19.0, 60.0, -15.0, 65.0)),
            subRectangles = listOf(rectA, rectB),
            ports =
                listOf(
                    SerializablePort(portCode = "PORT1", name = "Test Port", point = SerializablePoint(-10.0, 55.0)),
                ),
        )
    }

    /** Wide enough to show both [richDataset] sub-rectangles (lon -20..20) across the canvas at once. */
    private val wideCamera = MapCamera(centerLon = 0.0, centerLat = 55.0, visibleWidthMetres = 8_000_000.0)

    @Test
    fun tappingASeaOverlappingRectangleSelectsItAndDrawsLandGridPortsAndThePill() {
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
}
