package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.map

import android.content.Context
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

/** Statistical sub area "Other" type-ahead story: Scenarios 1–5 and FR1–FR8. */
class StatisticalSubAreaOtherSearchTest {
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

    private val seaAreas =
        listOf(
            area("38E95", ices = "38E9", lon = 0.75, lat = 54.75),
            area("38E98", ices = "38E9", lon = 0.25, lat = 54.60),
            area("38F02", ices = "", lon = 1.10, lat = 54.85),
            area("37E85", ices = "37E8", lon = -1.50, lat = 54.30),
        )
    private val landOnlyArea = area("40E01", ices = "40E0", lon = -2.00, lat = 55.50, isSea = false)

    private fun area(
        code: String,
        ices: String,
        lon: Double,
        lat: Double,
        isSea: Boolean = true,
    ): SerializableSubRectangle {
        val ring =
            SerializableRing(
                listOf(
                    SerializablePoint(lon - 0.1, lat - 0.1),
                    SerializablePoint(lon + 0.1, lat - 0.1),
                    SerializablePoint(lon + 0.1, lat + 0.1),
                    SerializablePoint(lon - 0.1, lat + 0.1),
                    SerializablePoint(lon - 0.1, lat - 0.1),
                ),
            )
        return SerializableSubRectangle(
            code = code,
            icesName = ices,
            areaKm2 = 1.0,
            bbox = SerializableBBox(lon - 0.1, lat - 0.1, lon + 0.1, lat + 0.1),
            centroid = SerializablePoint(lon, lat),
            geometry = SerializableMultiPolygon(listOf(SerializablePolygon(ring, emptyList()))),
            isSeaOverlapping = isSea,
        )
    }

    private val dataset =
        MapDataset(
            formatVersion = MapDataset.CURRENT_FORMAT_VERSION,
            land = emptyList(),
            subRectangles = seaAreas + landOnlyArea,
            ports = listOf(SerializablePort("GBHAS", "Hastings", SerializablePoint(0.6, 50.85))),
        )

    private val otherRadioTag = "${MapScreenTestTags.RADIO_OPTION_PREFIX}_${seaAreas.size}"

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

    private fun openSuggestedList() {
        composeTestRule.onNodeWithTag(MapScreenTestTags.GRID_OTHER_ACTION).performScrollTo().performClick()
    }

    private fun openOtherSearch() {
        openSuggestedList()
        composeTestRule.onNodeWithTag(otherRadioTag).performScrollTo().performClick()
    }

    private fun field() = composeTestRule.onNodeWithTag(MapScreenTestTags.AUTOCOMPLETE_FIELD).performScrollTo()

    private fun suggestion(index: Int) = composeTestRule.onNodeWithTag("${MapScreenTestTags.SUGGESTION_PREFIX}_$index")

    private fun coordinates() =
        composeTestRule
            .onNodeWithTag(MapScreenTestTags.AUTOCOMPLETE_COORDINATES)
            .performScrollTo()

    private fun noAreaSelected() = context.getString(R.string.gear_stat_rectangle_selected_area_none)

    // --- Scenario 1 / FR1 / FR2 -------------------------------------------------------------------------

    @Test
    fun otherOptionIsShownBeneathTheSuggestedSubAreaList() {
        setScreen()
        openSuggestedList()

        val other = composeTestRule.onNodeWithTag(otherRadioTag).performScrollTo()
        other.assertTextContains(context.getString(R.string.gear_stat_rectangle_other_option))
        val lastSuggestedBottom =
            composeTestRule
                .onNodeWithTag("${MapScreenTestTags.RADIO_OPTION_PREFIX}_${seaAreas.size - 1}")
                .getUnclippedBoundsInRoot()
                .bottom
        assertTrue(other.getUnclippedBoundsInRoot().top >= lastSuggestedBottom)
    }

    @Test
    fun selectingOtherRevealsAnEmptyTypeAheadField() {
        setScreen()
        composeTestRule.onNodeWithTag(MapScreenTestTags.AUTOCOMPLETE_FIELD).assertDoesNotExist()

        openOtherSearch()

        field()
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        composeTestRule.onNodeWithTag(MapScreenTestTags.RADIO_SAVE_ACTION).assertDoesNotExist()
    }

    @Test
    fun fisherCanManuallyTypeASubAreaCodeIntoTheField() {
        setScreen()
        openOtherSearch()

        field().performTextInput("37E85")

        field().assertTextContains("37E85")
    }

    // --- Scenario 2 / FR3 / FR4 / FR5 -------------------------------------------------------------------

    @Test
    fun oneCharacterShowsNoResultsAndPromptsForMore() {
        setScreen()
        openOtherSearch()

        field().performTextInput("3")

        suggestion(0).assertDoesNotExist()
        composeTestRule.onNodeWithTag(MapScreenTestTags.NO_MATCHES).assertDoesNotExist()
        composeTestRule
            .onNodeWithTag(MapScreenTestTags.LIVE_REGION)
            .assertTextEquals(
                context.resources.getQuantityString(R.plurals.port_search_type_more_characters, 2, 2),
            )
    }

    @Test
    fun twoCharactersShowAllMatchingSubAreasInADropdown() {
        setScreen()
        openOtherSearch()

        field().performTextInput("38")

        listOf("38E95", "38E98", "38F02").forEachIndexed { index, code ->
            suggestion(index).performScrollTo().assertIsDisplayed().assertTextContains(code, substring = true)
        }
        suggestion(3).assertDoesNotExist()
        composeTestRule
            .onNodeWithTag(MapScreenTestTags.LIVE_REGION)
            .assertTextEquals(context.resources.getQuantityString(R.plurals.port_search_suggestions_available, 3, 3))
    }

    @Test
    fun resultsNarrowInRealTimeAsMoreCharactersAreTyped() {
        setScreen()
        openOtherSearch()

        field().performTextInput("38")
        suggestion(2).assertExists()

        field().performTextInput("E9")
        suggestion(0).assertTextContains("38E95", substring = true)
        suggestion(1).assertTextContains("38E98", substring = true)
        suggestion(2).assertDoesNotExist()

        field().performTextInput("8")
        suggestion(0).assertTextContains("38E98", substring = true)
        suggestion(1).assertDoesNotExist()
    }

    @Test
    fun eachResultShowsCodeCoordinatesAndIcesRectangle() {
        setScreen()
        openOtherSearch()

        field().performTextInput("38E9")

        suggestion(0)
            .performScrollTo()
            .assertTextContains("38E95")
            .assertTextContains("ICES rectangle 38E9, centre 54.75°N, 0.75°E")
        suggestion(1)
            .performScrollTo()
            .assertTextContains("38E98")
            .assertTextContains("ICES rectangle 38E9, centre 54.60°N, 0.25°E")
    }

    @Test
    fun resultWithoutAnIcesRectangleStillShowsItsCoordinates() {
        setScreen()
        openOtherSearch()

        field().performTextInput("38F")

        suggestion(0)
            .performScrollTo()
            .assertTextContains("38F02")
            .assertTextContains("Centre 54.85°N, 1.10°E")
    }

    @Test
    fun resultsAreReadAloudWithCoordinatesInFullWords() {
        setScreen()
        openOtherSearch()

        field().performTextInput("37E")

        suggestion(0).assertContentDescriptionEquals(
            "37E85, ICES rectangle 37E8, centre 54.30 degrees north, 1.50 degrees west",
        )
    }

    @Test
    fun searchMatchesAnywhereInTheCodeAndIgnoresCase() {
        setScreen()
        openOtherSearch()

        field().performTextInput("e8")

        suggestion(0).assertTextContains("37E85", substring = true)
        suggestion(1).assertDoesNotExist()
    }

    @Test
    fun noMatchingSubAreasShowsANoMatchesMessage() {
        setScreen()
        openOtherSearch()

        field().performTextInput("99")

        suggestion(0).assertDoesNotExist()
        composeTestRule
            .onNodeWithTag(MapScreenTestTags.NO_MATCHES)
            .assertIsDisplayed()
            .assertTextEquals(context.getString(R.string.gear_stat_rectangle_no_matches_found))
    }

    @Test
    fun landOnlySubAreasAreNotOfferedAsResults() {
        setScreen()
        openOtherSearch()

        field().performTextInput("40E")

        suggestion(0).assertDoesNotExist()
        composeTestRule.onNodeWithTag(MapScreenTestTags.NO_MATCHES).assertIsDisplayed()
    }

    // --- Scenario 3 / FR6 -------------------------------------------------------------------------------

    @Test
    fun coordinatesAreNotShownForAPartialCode() {
        setScreen()
        openOtherSearch()

        field().performTextInput("38E9")

        coordinates().assertTextEquals(noAreaSelected())
    }

    @Test
    fun coordinatesAppearAutomaticallyWhenAValidCodeIsTyped() {
        setScreen()
        openOtherSearch()

        field().performTextInput("38E95")

        coordinates().assertTextEquals("Centre of area: 54.75°N, 0.75°E")
    }

    @Test
    fun coordinatesUpdateDynamicallyAsTheCodeChanges() {
        setScreen()
        openOtherSearch()

        field().performTextInput("38E9")
        coordinates().assertTextEquals(noAreaSelected())

        field().performTextInput("5")
        coordinates().assertTextEquals("Centre of area: 54.75°N, 0.75°E")

        field().performTextReplacement("38E98")
        coordinates().assertTextEquals("Centre of area: 54.60°N, 0.25°E")

        field().performTextInput("1")
        coordinates().assertTextEquals(noAreaSelected())
    }

    @Test
    fun coordinatesAppearForALowercaseCode() {
        setScreen()
        openOtherSearch()

        field().performTextInput("37e85")

        coordinates().assertTextEquals("Centre of area: 54.30°N, 1.50°W")
    }

    @Test
    fun coordinatesAreAnnouncedToScreenReaders() {
        setScreen()
        openOtherSearch()

        field().performTextInput("38E95")
        composeTestRule.mainClock.advanceTimeBy(500L)

        composeTestRule
            .onNodeWithTag(MapScreenTestTags.COORDINATES_LIVE_REGION)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assertTextEquals("Centre of area, 54.75 degrees north, 0.75 degrees east")
    }

    // --- Scenario 4 / FR7 -------------------------------------------------------------------------------

    @Test
    fun selectingAResultPopulatesTheFieldAndShowsItsCoordinates() {
        setScreen()
        openOtherSearch()

        field().performTextInput("38E9")
        suggestion(1).performScrollTo().performClick()

        field().assertTextContains("38E98")
        suggestion(0).assertDoesNotExist()
        coordinates().assertTextEquals("Centre of area: 54.60°N, 0.25°E")
    }

    @Test
    fun selectedResultSavesCodeAndCoordinatesAgainstTheCatchRecord() {
        var submitted: CatchRecordDraft? = null
        setScreen(onSubmit = { submitted = it })
        openOtherSearch()

        field().performTextInput("38E9")
        suggestion(1).performScrollTo().performClick()
        composeTestRule.onNodeWithTag(MapScreenTestTags.AUTOCOMPLETE_SAVE_ACTION).performScrollTo().performClick()

        val saved = submitted?.gearUses?.single()
        assertEquals("38E98", saved?.statisticalSubRectangleCode)
        assertEquals(54.60, saved?.statisticalSubRectangleLatitude)
        assertEquals(0.25, saved?.statisticalSubRectangleLongitude)
    }

    @Test
    fun typedValidCodeSavesNormalisedCodeAndCoordinates() {
        var submitted: CatchRecordDraft? = null
        setScreen(onSubmit = { submitted = it })
        openOtherSearch()

        field().performTextInput("37e85")
        composeTestRule.onNodeWithTag(MapScreenTestTags.AUTOCOMPLETE_SAVE_ACTION).performScrollTo().performClick()

        val saved = submitted?.gearUses?.single()
        assertEquals("37E85", saved?.statisticalSubRectangleCode)
        assertEquals(54.30, saved?.statisticalSubRectangleLatitude)
        assertEquals(-1.50, saved?.statisticalSubRectangleLongitude)
    }

    // --- Scenario 5 / FR8 -------------------------------------------------------------------------------

    @Test
    fun unknownCodeIsRejectedWithTheValidSubAreaMessage() {
        var submitted: CatchRecordDraft? = null
        setScreen(onSubmit = { submitted = it })
        openOtherSearch()

        field().performTextInput("99Z99")
        composeTestRule.onNodeWithTag(MapScreenTestTags.AUTOCOMPLETE_SAVE_ACTION).performScrollTo().performClick()

        composeTestRule.onNodeWithTag(MapScreenTestTags.ERROR_SUMMARY).assertIsDisplayed()
        composeTestRule.onNodeWithText("Enter a valid statistical sub area code.").assertIsDisplayed()
        assertNull(submitted)
    }

    @Test
    fun landOnlyCodeIsRejectedAsNotInTheReferenceDataset() {
        var submitted: CatchRecordDraft? = null
        setScreen(onSubmit = { submitted = it })
        openOtherSearch()

        field().performTextInput("40E01")
        composeTestRule.onNodeWithTag(MapScreenTestTags.AUTOCOMPLETE_SAVE_ACTION).performScrollTo().performClick()

        composeTestRule.onNodeWithText("Enter a valid statistical sub area code.").assertIsDisplayed()
        assertNull(submitted)
    }

    @Test
    fun invalidCodeErrorClearsWhenTheFisherEditsTheField() {
        setScreen()
        openOtherSearch()

        field().performTextInput("99Z99")
        composeTestRule.onNodeWithTag(MapScreenTestTags.AUTOCOMPLETE_SAVE_ACTION).performScrollTo().performClick()
        composeTestRule.onNodeWithTag(MapScreenTestTags.ERROR_SUMMARY).assertIsDisplayed()

        field().performTextReplacement("38E95")

        composeTestRule.onNodeWithTag(MapScreenTestTags.ERROR_SUMMARY).assertDoesNotExist()
        coordinates().assertTextEquals("Centre of area: 54.75°N, 0.75°E")
    }
}
