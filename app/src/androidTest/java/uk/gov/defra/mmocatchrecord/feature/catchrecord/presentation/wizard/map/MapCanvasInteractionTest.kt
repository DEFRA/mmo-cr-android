package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.map

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.StatisticalSubRectangle
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardTestTheme
import uk.gov.defra.mmocatchrecord.mapdata.MapDataset
import uk.gov.defra.mmocatchrecord.mapdata.SerializableBBox
import uk.gov.defra.mmocatchrecord.mapdata.SerializableMultiPolygon
import uk.gov.defra.mmocatchrecord.mapdata.SerializablePoint
import uk.gov.defra.mmocatchrecord.mapdata.SerializablePolygon
import uk.gov.defra.mmocatchrecord.mapdata.SerializableRing
import uk.gov.defra.mmocatchrecord.mapdata.SerializableSubRectangle

/** Gesture-level UI tests for the interactive offline map (tap, drag-to-pan, pinch-to-zoom, TalkBack action). */
@RunWith(AndroidJUnit4::class)
class MapCanvasInteractionTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private class MapHost(
        initialCamera: MapCamera,
    ) {
        var camera by mutableStateOf(initialCamera)
        var selectedCode by mutableStateOf<String?>(null)
        var chooseFromListRequests = 0
    }

    // Camera sits on the shared edge (lon 0): the west half of the canvas is a sea cell, the east half is land-only.
    private val initialCamera =
        MapCamera(centerLon = 0.0, centerLat = 55.0, visibleWidthMetres = MapCamera.DEFAULT_VISIBLE_WIDTH_METRES)

    private fun subRectangle(
        code: String,
        minLon: Double,
        maxLon: Double,
        isSeaOverlapping: Boolean,
    ): SerializableSubRectangle {
        val ring =
            SerializableRing(
                listOf(
                    SerializablePoint(minLon, 30.0),
                    SerializablePoint(maxLon, 30.0),
                    SerializablePoint(maxLon, 75.0),
                    SerializablePoint(minLon, 75.0),
                    SerializablePoint(minLon, 30.0),
                ),
            )
        return SerializableSubRectangle(
            code = code,
            icesName = "Test area",
            areaKm2 = 1.0,
            bbox = SerializableBBox(minLon = minLon, minLat = 30.0, maxLon = maxLon, maxLat = 75.0),
            centroid = SerializablePoint((minLon + maxLon) / 2.0, 52.5),
            geometry = SerializableMultiPolygon(listOf(SerializablePolygon(ring, emptyList()))),
            isSeaOverlapping = isSeaOverlapping,
        )
    }

    private fun splitDataset() =
        MapDataset(
            formatVersion = MapDataset.CURRENT_FORMAT_VERSION,
            land = emptyList(),
            subRectangles =
                listOf(
                    subRectangle(SEA_CODE, minLon = -20.0, maxLon = 0.0, isSeaOverlapping = true),
                    subRectangle(LAND_ONLY_CODE, minLon = 0.0, maxLon = 20.0, isSeaOverlapping = false),
                ),
            ports = emptyList(),
        )

    private fun setMap(host: MapHost) {
        composeTestRule.setContent {
            WizardTestTheme {
                MapCanvas(
                    dataset = splitDataset(),
                    cameraState = MapCameraState(host.camera) { host.camera = it },
                    selection = MapSelectionState(host.selectedCode) { host.selectedCode = it },
                    modifier = Modifier.fillMaxWidth(),
                    onChooseFromListRequested = { host.chooseFromListRequests++ },
                )
            }
        }
    }

    private fun mapNode() = composeTestRule.onNodeWithTag(MapScreenTestTags.MAP)

    private fun tapWestHalf() = mapNode().performTouchInput { click(Offset(width * 0.25f, centerY)) }

    private fun tapEastHalf() = mapNode().performTouchInput { click(Offset(width * 0.75f, centerY)) }

    @Test
    fun tappingASeaCellSelectsItAndAnnouncesItInTheContentDescription() {
        val host = MapHost(initialCamera)
        setMap(host)

        mapNode().assertContentDescriptionContains("No area selected", substring = true)
        tapWestHalf()

        assertEquals(SEA_CODE, host.selectedCode)
        mapNode().assertContentDescriptionContains("Selected area: $SEA_CODE", substring = true)
    }

    @Test
    fun tappingALandOnlyCellDoesNotSelectIt() {
        val host = MapHost(initialCamera)
        setMap(host)

        tapEastHalf()

        assertNull(host.selectedCode)
        mapNode().assertContentDescriptionContains("No area selected", substring = true)
    }

    @Test
    fun tappingOutsideAnySelectableCellClearsTheCurrentSelection() {
        val host = MapHost(initialCamera)
        setMap(host)

        tapWestHalf()
        assertEquals(SEA_CODE, host.selectedCode)
        tapEastHalf()

        assertNull(host.selectedCode)
    }

    @Test
    fun draggingPansTheCameraWithoutChangingZoomOrSelection() {
        val host = MapHost(initialCamera)
        setMap(host)

        mapNode().performTouchInput {
            swipe(start = center, end = center - Offset(width * 0.4f, 0f), durationMillis = GESTURE_DURATION_MS)
        }
        Espresso.onIdle()

        assertTrue("Swiping left should move the camera east", host.camera.centerLon > initialCamera.centerLon)
        assertEquals(initialCamera.centerLat, host.camera.centerLat, COORDINATE_TOLERANCE)
        assertEquals(initialCamera.visibleWidthMetres, host.camera.visibleWidthMetres, WIDTH_TOLERANCE_METRES)
        assertNull("A drag must not be treated as a tap", host.selectedCode)
    }

    @Test
    fun draggingAfterSelectingKeepsTheSelectedCell() {
        val host = MapHost(initialCamera)
        setMap(host)

        tapWestHalf()
        mapNode().performTouchInput {
            swipe(start = center, end = center + Offset(0f, height * 0.3f), durationMillis = GESTURE_DURATION_MS)
        }

        assertEquals(SEA_CODE, host.selectedCode)
    }

    @Test
    fun pinchingOutZoomsInButNotBeyondTwoSubRectanglesAcross() {
        val host = MapHost(initialCamera)
        setMap(host)

        mapNode().performTouchInput {
            pinch(
                start0 = center - Offset(PINCH_NEAR_PX, 0f),
                end0 = center - Offset(width * 0.45f, 0f),
                start1 = center + Offset(PINCH_NEAR_PX, 0f),
                end1 = center + Offset(width * 0.45f, 0f),
                durationMillis = GESTURE_DURATION_MS,
            )
        }

        assertEquals(MapCamera.MIN_VISIBLE_WIDTH_METRES, host.camera.visibleWidthMetres, WIDTH_TOLERANCE_METRES)
        assertNull("A pinch must not be treated as a tap", host.selectedCode)
    }

    @Test
    fun pinchingInZoomsOutButNotBeyondFourSubRectanglesAcross() {
        val host = MapHost(initialCamera)
        setMap(host)

        mapNode().performTouchInput {
            pinch(
                start0 = center - Offset(width * 0.45f, 0f),
                end0 = center - Offset(PINCH_NEAR_PX, 0f),
                start1 = center + Offset(width * 0.45f, 0f),
                end1 = center + Offset(PINCH_NEAR_PX, 0f),
                durationMillis = GESTURE_DURATION_MS,
            )
        }

        assertEquals(MapCamera.MAX_VISIBLE_WIDTH_METRES, host.camera.visibleWidthMetres, WIDTH_TOLERANCE_METRES)
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun chooseFromListAccessibilityActionRequestsTheListFallback() {
        val host = MapHost(initialCamera)
        setMap(host)
        val actionLabel =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext
                .getString(R.string.gear_stat_rectangle_map_choose_from_list_action)

        mapNode().performCustomAccessibilityActionWithLabel(actionLabel)

        assertEquals(1, host.chooseFromListRequests)
        assertNull(host.selectedCode)
    }

    @Test
    fun mapScreenSubmitsTheTappedCellAfterTheUserPansTheMap() {
        val gearType = GearType(id = "gear-handlines", name = "Handlines")
        val draft =
            CatchRecordDraft(
                id = "draft-1",
                vesselId = "vessel-1",
                gearUses =
                    listOf(
                        GearUse(
                            id = "gear-use-1",
                            gearTypeId = gearType.id,
                            statisticalSubRectangleCode = null,
                            confirmedUsedOnTrip = true,
                        ),
                    ),
                modifiedAtEpochMillis = 0L,
                status = DraftStatus.Draft,
            )
        val state =
            CatchRecordFlowViewState(
                status = UiStatus.Content(draft),
                gearTypes = listOf(gearType),
                statisticalSubRectangles = listOf(StatisticalSubRectangle("rect-1", SEA_CODE, "AREA-1")),
            )
        val dataset =
            MapDataset(
                formatVersion = MapDataset.CURRENT_FORMAT_VERSION,
                land = emptyList(),
                subRectangles = listOf(subRectangle(SEA_CODE, minLon = -20.0, maxLon = 20.0, isSeaOverlapping = true)),
                ports = emptyList(),
            )
        var submittedDraft: CatchRecordDraft? = null
        composeTestRule.setContent {
            WizardTestTheme {
                MapScreen(
                    state = state,
                    onSubmit = { submittedDraft = it },
                    onBack = {},
                    mapDataState = MapDataUiState(UiStatus.Content(dataset), onRetryMapData = {}),
                )
            }
        }

        mapNode().performTouchInput { click(center) }
        mapNode().performTouchInput {
            swipe(start = center, end = center - Offset(width * 0.3f, 0f), durationMillis = GESTURE_DURATION_MS)
        }
        composeTestRule
            .onNodeWithTag(MapScreenTestTags.MAP_SELECTED_TEXT)
            .assertTextContains("Selected area: $SEA_CODE")
        composeTestRule.onNodeWithTag(MapScreenTestTags.GRID_SAVE_ACTION).performScrollTo().performClick()

        assertEquals(SEA_CODE, submittedDraft?.gearUses?.single()?.statisticalSubRectangleCode)
    }

    private companion object {
        const val SEA_CODE = "38E95"
        const val LAND_ONLY_CODE = "38F00"
        const val GESTURE_DURATION_MS = 400L
        const val PINCH_NEAR_PX = 20f
        const val COORDINATE_TOLERANCE = 1e-3
        const val WIDTH_TOLERANCE_METRES = 1.0
    }
}
