package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.map

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelection
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelectionMode
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardTestTheme
import uk.gov.defra.mmocatchrecord.mapdata.MapDataset
import uk.gov.defra.mmocatchrecord.mapdata.SerializableBBox
import uk.gov.defra.mmocatchrecord.mapdata.SerializableMultiPolygon
import uk.gov.defra.mmocatchrecord.mapdata.SerializablePoint
import uk.gov.defra.mmocatchrecord.mapdata.SerializablePolygon
import uk.gov.defra.mmocatchrecord.mapdata.SerializablePort
import uk.gov.defra.mmocatchrecord.mapdata.SerializableRing
import uk.gov.defra.mmocatchrecord.mapdata.SerializableSubRectangle

/** Statistical sub area map selection: select on the map, save it, and the required error on Save and continue. */
class StatisticalSubAreaMapAndListSelectionTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val gearType = GearType(id = "gear-otter", name = "Otter trawls")
    private val hastings = Port("port-hastings", "Hastings", "AREA-HASTINGS")
    private val gearUse =
        GearUse(
            id = "gear-use-1",
            gearTypeId = gearType.id,
            statisticalSubRectangleCode = null,
            confirmedUsedOnTrip = true,
        )

    // The camera centres on the port (lon 0), so the west half of the map is WEST_CODE and the east half EAST_CODE.
    private val dataset =
        MapDataset(
            formatVersion = MapDataset.CURRENT_FORMAT_VERSION,
            land = emptyList(),
            subRectangles =
                listOf(
                    area(WEST_CODE, minLon = -20.0, maxLon = 0.0),
                    area(EAST_CODE, minLon = 0.0, maxLon = 20.0),
                ),
            ports = listOf(SerializablePort("GBHAS", "Hastings", SerializablePoint(0.0, 55.0))),
        )

    private fun area(
        code: String,
        minLon: Double,
        maxLon: Double,
    ): SerializableSubRectangle {
        val ring =
            SerializableRing(
                listOf(
                    SerializablePoint(minLon, MIN_LAT),
                    SerializablePoint(maxLon, MIN_LAT),
                    SerializablePoint(maxLon, MAX_LAT),
                    SerializablePoint(minLon, MAX_LAT),
                    SerializablePoint(minLon, MIN_LAT),
                ),
            )
        return SerializableSubRectangle(
            code = code,
            icesName = code.take(4),
            areaKm2 = 1.0,
            bbox = SerializableBBox(minLon, MIN_LAT, maxLon, MAX_LAT),
            centroid = SerializablePoint((minLon + maxLon) / 2.0, CENTROID_LAT),
            geometry = SerializableMultiPolygon(listOf(SerializablePolygon(ring, emptyList()))),
            isSeaOverlapping = true,
        )
    }

    private fun setScreen(onSubmit: (CatchRecordDraft) -> Unit = {}) {
        val draft =
            CatchRecordDraft(
                id = "draft-1",
                vesselId = "vessel-1",
                departurePort = PortSelection(hastings.id, PortSelectionMode.FirstTime),
                gearUses = listOf(gearUse),
                modifiedAtEpochMillis = 0L,
                status = DraftStatus.Draft,
            )
        composeTestRule.setContent {
            WizardTestTheme {
                MapScreen(
                    state =
                        CatchRecordFlowViewState(
                            status = UiStatus.Content(draft),
                            gearTypes = listOf(gearType),
                            ports = listOf(hastings),
                        ),
                    onSubmit = onSubmit,
                    onBack = {},
                    mapDataState = MapDataUiState(UiStatus.Content(dataset), onRetryMapData = {}),
                )
            }
        }
    }

    private fun map() = composeTestRule.onNodeWithTag(MapScreenTestTags.MAP)

    // Tap the upper part of the map so the "Other" button pinned to its bottom-left is never hit.
    private fun tapMapWestHalf() =
        map().performScrollTo().performTouchInput { click(Offset(width * 0.25f, height * 0.3f)) }

    private fun tapMapEastHalf() =
        map().performScrollTo().performTouchInput { click(Offset(width * 0.75f, height * 0.3f)) }

    private fun selectedText() = composeTestRule.onNodeWithTag(MapScreenTestTags.MAP_SELECTED_TEXT).performScrollTo()

    private fun saveAndContinue() =
        composeTestRule.onNodeWithTag(MapScreenTestTags.GRID_SAVE_ACTION).performScrollTo().performClick()

    private fun errorMessage() = composeTestRule.onNodeWithTag(MapScreenTestTags.ERROR_MESSAGE)

    private fun selectedArea(code: String) = context.getString(R.string.gear_stat_rectangle_selected_area, code)

    private fun noAreaSelected() = context.getString(R.string.gear_stat_rectangle_selected_area_none)

    // --- Select area using the map (FR2 / FR3 / FR4) ----------------------------------------------------

    @Test
    fun mapIsShownWithNoAreaSelectedInitially() {
        setScreen()

        map().performScrollTo().assertIsDisplayed().assertContentDescriptionContains(noAreaSelected(), substring = true)
        selectedText().assertTextEquals(noAreaSelected())
    }

    @Test
    fun tappingAnAreaOnTheMapSelectsAndHighlightsIt() {
        setScreen()

        tapMapWestHalf()

        map().assertContentDescriptionContains(selectedArea(WEST_CODE), substring = true)
        selectedText().assertTextEquals(selectedArea(WEST_CODE))
    }

    @Test
    fun tappingADifferentAreaOnTheMapReplacesTheSelection() {
        setScreen()

        tapMapWestHalf()
        tapMapEastHalf()

        map().assertContentDescriptionContains(selectedArea(EAST_CODE), substring = true)
        selectedText().assertTextEquals(selectedArea(EAST_CODE))
    }

    @Test
    fun saveAndContinueAttachesTheMapSelectedAreaToTheCatchRecord() {
        var submitted: CatchRecordDraft? = null
        setScreen(onSubmit = { submitted = it })

        tapMapWestHalf()
        saveAndContinue()

        assertEquals(WEST_CODE, submitted?.gearUses?.single()?.statisticalSubRectangleCode)
    }

    @Test
    fun saveAndContinueSavesTheLatestMapSelection() {
        var submitted: CatchRecordDraft? = null
        setScreen(onSubmit = { submitted = it })

        tapMapWestHalf()
        tapMapEastHalf()
        saveAndContinue()

        assertEquals(EAST_CODE, submitted?.gearUses?.single()?.statisticalSubRectangleCode)
    }

    // --- Save and continue without selecting an area (FR11) ---------------------------------------------

    @Test
    fun noErrorIsShownBeforeSaveAndContinueIsSelected() {
        setScreen()

        errorMessage().assertDoesNotExist()
    }

    @Test
    fun saveAndContinueWithoutSelectingAnAreaShowsTheRequiredError() {
        setScreen()

        saveAndContinue()

        errorMessage().performScrollTo().assertIsDisplayed().assertTextEquals(REQUIRED_ERROR)
    }

    @Test
    fun saveAndContinueWithoutSelectingAnAreaKeepsTheFisherOnTheSamePage() {
        var submitted: CatchRecordDraft? = null
        setScreen(onSubmit = { submitted = it })

        saveAndContinue()

        assertNull(submitted)
        map().assertExists()
        composeTestRule.onNodeWithTag(MapScreenTestTags.GRID_SAVE_ACTION).assertExists()
    }

    @Test
    fun requiredErrorClearsOnceAnAreaIsSelectedOnTheMap() {
        setScreen()

        saveAndContinue()
        errorMessage().assertExists()
        tapMapWestHalf()

        errorMessage().assertDoesNotExist()
    }

    @Test
    fun saveAndContinueSucceedsAfterCorrectingTheRequiredError() {
        var submitted: CatchRecordDraft? = null
        setScreen(onSubmit = { submitted = it })

        saveAndContinue()
        tapMapEastHalf()
        saveAndContinue()

        assertEquals(EAST_CODE, submitted?.gearUses?.single()?.statisticalSubRectangleCode)
    }

    private companion object {
        const val WEST_CODE = "38E95"
        const val EAST_CODE = "38F00"
        const val MIN_LAT = 30.0
        const val MAX_LAT = 75.0
        const val CENTROID_LAT = 52.5
        const val REQUIRED_ERROR = "Select the statistical sub area where the majority of your catch was caught."
    }
}
