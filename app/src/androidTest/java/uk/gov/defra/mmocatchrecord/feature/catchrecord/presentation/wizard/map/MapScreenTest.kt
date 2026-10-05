@file:Suppress("detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.map

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelection
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelectionMode
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldKeys
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port
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

class MapScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val seineNets = GearType(id = "gear-seine-nets", name = "Seine nets (not specified)")
    private val samplePort = Port("port-hastings", "Hastings", "AREA-HASTINGS")
    private val sampleRectangles =
        listOf(
            StatisticalSubRectangle("rect-1", "38E95", "AREA-HASTINGS"),
            StatisticalSubRectangle("rect-2", "38E98", "AREA-HASTINGS"),
            StatisticalSubRectangle("rect-3", "38F02", "AREA-HASTINGS"),
        )

    private fun pendingGearUse(confirmed: Boolean = true) =
        GearUse(
            id = "gear-use-1",
            gearTypeId = seineNets.id,
            statisticalSubRectangleCode = null,
            measurements = mapOf(GearMeasurementFieldKeys.MESH_SIZE_MM to MeasurementValue.Numeric(100.0, "mm")),
            confirmedUsedOnTrip = confirmed,
        )

    private fun stateWith(
        gearUse: GearUse,
        rectangles: List<StatisticalSubRectangle> = sampleRectangles,
    ): CatchRecordFlowViewState {
        val draft =
            CatchRecordDraft(
                id = "draft-1",
                vesselId = "vessel-1",
                departurePort = PortSelection(samplePort.id, PortSelectionMode.FirstTime),
                gearUses = listOf(gearUse),
                modifiedAtEpochMillis = 0L,
                status = DraftStatus.Draft,
            )
        return CatchRecordFlowViewState(
            status = UiStatus.Content(draft),
            gearTypes = listOf(seineNets),
            ports = listOf(samplePort),
            statisticalSubRectangles = rectangles,
        )
    }

    /**
     * A minimal, synthetic [MapDataset] for Canvas Grid-mode interaction tests — one huge sea-overlapping
     * sub-rectangle spanning the whole of [MapCameraSupport]'s UK-waters default center (and any
     * port-match/nearby-bbox center this fixture's [samplePort]/[sampleRectangles] could resolve to), so a
     * tap at the center of the rendered [MapCanvas] always lands inside it regardless of which of the three
     * initial-camera strategies applies — see [MapCameraSupport.initialCameraFor].
     */
    private fun mapDatasetFixture(): MapDataset {
        val ring =
            SerializableRing(
                listOf(
                    SerializablePoint(-20.0, 30.0),
                    SerializablePoint(20.0, 30.0),
                    SerializablePoint(20.0, 75.0),
                    SerializablePoint(-20.0, 75.0),
                    SerializablePoint(-20.0, 30.0),
                ),
            )
        val subRectangle =
            SerializableSubRectangle(
                code = "38E95",
                icesName = "Test area",
                areaKm2 = 1.0,
                bbox = SerializableBBox(minLon = -20.0, minLat = 30.0, maxLon = 20.0, maxLat = 75.0),
                centroid = SerializablePoint(0.0, 55.0),
                geometry = SerializableMultiPolygon(listOf(SerializablePolygon(ring, emptyList()))),
                isSeaOverlapping = true,
            )
        return MapDataset(
            formatVersion = MapDataset.CURRENT_FORMAT_VERSION,
            land = emptyList(),
            subRectangles = listOf(subRectangle),
            ports = emptyList(),
        )
    }

    // --- Screen 1: the offline Canvas map --------------------------------------------------------------

    @Test
    fun gridScreenShowsIdentifyingMeasurementInTitleAndSubmitsTappedCell() {
        var submittedDraft: CatchRecordDraft? = null
        composeTestRule.setContent {
            WizardTestTheme {
                MapScreen(
                    state = stateWith(pendingGearUse()),
                    onSubmit = { submittedDraft = it },
                    onBack = {},
                    mapDataState = MapDataUiState(UiStatus.Content(mapDatasetFixture()), onRetryMapData = {}),
                )
            }
        }

        composeTestRule
            .onNodeWithText("Where was the majority of your catch caught using seine nets (mesh size 100mm)?")
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MapScreenTestTags.MAP).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MapScreenTestTags.MAP).performTouchInput { click() }
        composeTestRule.onNodeWithTag(MapScreenTestTags.MAP_SELECTED_TEXT).assertTextContains("38E95", substring = true)
        // With map data loaded the map pushes the Save action below the fold of the scrolling wizard column.
        composeTestRule.onNodeWithTag(MapScreenTestTags.GRID_SAVE_ACTION).performScrollTo().performClick()

        assertEquals("38E95", submittedDraft?.gearUses?.single()?.statisticalSubRectangleCode)
    }

    @Test
    fun gridScreenWithNoIdentifyingMeasurementOmitsParenthetical() {
        val handlines =
            GearType(
                id = "gear-handlines",
                name = "Handlines and pole lines (hand operated)",
                measurementTitleOverride = "handlines",
            )
        val gearUse =
            GearUse(
                id = "gear-use-1",
                gearTypeId = handlines.id,
                statisticalSubRectangleCode = null,
                confirmedUsedOnTrip = true,
            )
        val draft =
            CatchRecordDraft(
                id = "draft-1",
                vesselId = "vessel-1",
                departurePort = PortSelection(samplePort.id, PortSelectionMode.FirstTime),
                gearUses = listOf(gearUse),
                modifiedAtEpochMillis = 0L,
                status = DraftStatus.Draft,
            )
        val state =
            CatchRecordFlowViewState(
                status = UiStatus.Content(draft),
                gearTypes = listOf(handlines),
                ports = listOf(samplePort),
                statisticalSubRectangles = sampleRectangles,
            )
        composeTestRule.setContent {
            WizardTestTheme {
                MapScreen(state = state, onSubmit = {}, onBack = {})
            }
        }

        composeTestRule
            .onNodeWithText("Where was the majority of your catch caught using handlines?")
            .assertIsDisplayed()
    }

    /**
     * Regression test for the reported "title always shows Seine nets" bug: a confirmed gear use for a
     * *different*, non-default gear type ("Bottom pair trawls (PTB)" — the exact gear named in the reported
     * bug/reference screenshot) must show its own name in the title, not "Seine nets". Root cause (see
     * `StubReferenceDataRepository`/`CatchRecordFlowViewModel.gearTypeSelected`'s doc comments): this gear
     * type previously had an empty `measurementFields` schema, making it silently unselectable, and an
     * invalid/unselectable selection left a *stale* `pendingGearTypeId` (typically "Seine nets", the
     * commonly-tried first search result) rather than clearing it — both are now fixed.
     */
    @Test
    fun gridScreenShowsANonDefaultConfirmedGearTitleNotSeineNets() {
        val bottomPairTrawls = GearType(id = "gear-bottom-pair-trawls-ptb", name = "Bottom pair trawls (PTB)")
        val gearUse =
            GearUse(
                id = "gear-use-1",
                gearTypeId = bottomPairTrawls.id,
                statisticalSubRectangleCode = null,
                confirmedUsedOnTrip = true,
            )
        val draft =
            CatchRecordDraft(
                id = "draft-1",
                vesselId = "vessel-1",
                departurePort = PortSelection(samplePort.id, PortSelectionMode.FirstTime),
                gearUses = listOf(gearUse),
                modifiedAtEpochMillis = 0L,
                status = DraftStatus.Draft,
            )
        val state =
            CatchRecordFlowViewState(
                status = UiStatus.Content(draft),
                gearTypes = listOf(bottomPairTrawls, seineNets),
                ports = listOf(samplePort),
                statisticalSubRectangles = sampleRectangles,
            )
        composeTestRule.setContent {
            WizardTestTheme {
                MapScreen(state = state, onSubmit = {}, onBack = {})
            }
        }

        // The title uses the lowercased display name, which strips the "(PTB)" reference-code suffix —
        // see GearMeasurementSupport.titleGearNameFor/displayNameFor.
        composeTestRule
            .onNodeWithText("Where was the majority of your catch caught using bottom pair trawls?")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Where was the majority of your catch caught using seine nets?")
            .assertDoesNotExist()
    }

    @Test
    fun gridScreenWithNoSelectionShowsRequiredErrorAndDoesNotSubmit() {
        var submittedDraft: CatchRecordDraft? = null
        composeTestRule.setContent {
            WizardTestTheme {
                MapScreen(
                    state = stateWith(pendingGearUse()),
                    onSubmit = { submittedDraft = it },
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MapScreenTestTags.GRID_SAVE_ACTION).performClick()
        composeTestRule.onNodeWithTag(MapScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
        assertNull(submittedDraft)
    }

    /**
     * Requirement: "if the map data fails to load, Grid mode must still work: show an accessible message
     * and keep 'Other' (→ RadioList) available; never crash" — see [MapGridContent].
     */
    @Test
    fun gridScreenWhenMapDataFailsToLoadStillShowsOtherAndAccessibleError() {
        composeTestRule.setContent {
            WizardTestTheme {
                MapScreen(
                    state = stateWith(pendingGearUse()),
                    onSubmit = {},
                    onBack = {},
                    mapDataState =
                        MapDataUiState(
                            UiStatus.Error(message = "Unable to load the offline map data", isRetryable = true),
                            onRetryMapData = {},
                        ),
                )
            }
        }

        composeTestRule.onNodeWithTag(MapScreenTestTags.MAP_ERROR).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MapScreenTestTags.GRID_OTHER_ACTION).assertIsDisplayed()
    }

    /**
     * Accessibility requirement: TalkBack/switch-access users who cannot perform the map's drag/pinch/tap
     * gestures must have an equivalent route to the radio-list selection screen, exposed as a Compose
     * custom accessibility action on the map's semantics node (see [MapCanvas]).
     */
    @Test
    @OptIn(ExperimentalTestApi::class)
    fun mapNodeExposesACustomAccessibilityActionToTheRadioList() {
        composeTestRule.setContent {
            WizardTestTheme {
                MapScreen(
                    state = stateWith(pendingGearUse()),
                    onSubmit = {},
                    onBack = {},
                    mapDataState = MapDataUiState(UiStatus.Content(mapDatasetFixture()), onRetryMapData = {}),
                )
            }
        }
        val actionLabel =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext
                .getString(R.string.gear_stat_rectangle_map_choose_from_list_action)

        composeTestRule
            .onNodeWithTag(MapScreenTestTags.MAP)
            .performCustomAccessibilityActionWithLabel(actionLabel)

        composeTestRule.onNodeWithTag("${MapScreenTestTags.RADIO_OPTION_PREFIX}_0").assertIsDisplayed()
    }

    // --- Screen 2: "Other" -> radio list ---------------------------------------------------------------

    private fun setMapScreen(onSubmit: (CatchRecordDraft) -> Unit = {}) {
        composeTestRule.setContent {
            WizardTestTheme {
                MapScreen(state = stateWith(pendingGearUse()), onSubmit = onSubmit, onBack = {})
            }
        }
    }

    @Test
    fun gridOtherLinkNavigatesToRadioListWithNearbyCodesAndOtherOption() {
        setMapScreen()

        composeTestRule.onNodeWithTag(MapScreenTestTags.GRID_OTHER_ACTION).performClick()

        composeTestRule
            .onNodeWithText(
                "Select the statistical sub area where the majority of your catch was caught using seine nets (mesh size 100mm)?",
            ).assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                "Select the area where most of your catch was caught. If it is not listed, select Other to enter it.",
            ).assertIsDisplayed()
        sampleRectangles.forEachIndexed { index, rectangle ->
            composeTestRule.onNodeWithTag("${MapScreenTestTags.RADIO_OPTION_PREFIX}_$index").assertIsDisplayed()
            composeTestRule.onNodeWithText(rectangle.code).assertIsDisplayed()
        }
        composeTestRule
            .onNodeWithTag("${MapScreenTestTags.RADIO_OPTION_PREFIX}_${sampleRectangles.size}")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Other").assertIsDisplayed()
    }

    @Test
    fun radioListSelectionSubmitsTheChosenCode() {
        var submittedDraft: CatchRecordDraft? = null
        setMapScreen(onSubmit = { submittedDraft = it })

        composeTestRule.onNodeWithTag(MapScreenTestTags.GRID_OTHER_ACTION).performClick()
        composeTestRule.onNodeWithTag("${MapScreenTestTags.RADIO_OPTION_PREFIX}_1").performClick()
        composeTestRule.onNodeWithTag(MapScreenTestTags.RADIO_SAVE_ACTION).performClick()

        assertEquals("38E98", submittedDraft?.gearUses?.single()?.statisticalSubRectangleCode)
    }

    @Test
    fun radioListWithNoSelectionShowsRequiredError() {
        var submittedDraft: CatchRecordDraft? = null
        setMapScreen(onSubmit = { submittedDraft = it })

        composeTestRule.onNodeWithTag(MapScreenTestTags.GRID_OTHER_ACTION).performClick()
        composeTestRule.onNodeWithTag(MapScreenTestTags.RADIO_SAVE_ACTION).performClick()

        composeTestRule.onNodeWithTag(MapScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
        assertNull(submittedDraft)
    }

    // --- Screen 3: "Other" (again) -> autocomplete search ----------------------------------------------

    @Test
    fun selectingOtherOnRadioListNavigatesToAutocompleteSearch() {
        var submittedDraft: CatchRecordDraft? = null
        setMapScreen(onSubmit = { submittedDraft = it })

        composeTestRule.onNodeWithTag(MapScreenTestTags.GRID_OTHER_ACTION).performClick()
        // Radio option index 3 is the trailing "Other" entry (3 nearby codes at indices 0-2).
        composeTestRule.onNodeWithTag("${MapScreenTestTags.RADIO_OPTION_PREFIX}_3").performClick()
        composeTestRule.onNodeWithTag(MapScreenTestTags.RADIO_SAVE_ACTION).performClick()

        composeTestRule.onNodeWithTag(MapScreenTestTags.AUTOCOMPLETE_FIELD).assertIsDisplayed()
        assertNull(submittedDraft)
    }

    @Test
    fun autocompleteScreenSubmitsACorrectlyFormattedTypedCodeNotJustSuggestionListEntries() {
        var submittedDraft: CatchRecordDraft? = null
        composeTestRule.setContent {
            WizardTestTheme {
                MapScreen(
                    state = stateWith(pendingGearUse()),
                    onSubmit = { submittedDraft = it },
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MapScreenTestTags.GRID_OTHER_ACTION).performClick()
        composeTestRule.onNodeWithTag("${MapScreenTestTags.RADIO_OPTION_PREFIX}_3").performClick()
        composeTestRule.onNodeWithTag(MapScreenTestTags.RADIO_SAVE_ACTION).performClick()

        // A code not present in the local nearby/stub list, entered via free text.
        composeTestRule.onNodeWithTag(MapScreenTestTags.AUTOCOMPLETE_FIELD).performTextInput("99Z99")
        composeTestRule.onNodeWithTag(MapScreenTestTags.AUTOCOMPLETE_SAVE_ACTION).performClick()

        assertEquals("99Z99", submittedDraft?.gearUses?.single()?.statisticalSubRectangleCode)
    }

    @Test
    fun autocompleteScreenWithBlankInputShowsRequiredErrorSummary() {
        composeTestRule.setContent {
            WizardTestTheme {
                MapScreen(state = stateWith(pendingGearUse()), onSubmit = {}, onBack = {})
            }
        }

        composeTestRule.onNodeWithTag(MapScreenTestTags.GRID_OTHER_ACTION).performClick()
        composeTestRule.onNodeWithTag("${MapScreenTestTags.RADIO_OPTION_PREFIX}_3").performClick()
        composeTestRule.onNodeWithTag(MapScreenTestTags.RADIO_SAVE_ACTION).performClick()

        composeTestRule.onNodeWithTag(MapScreenTestTags.AUTOCOMPLETE_SAVE_ACTION).performClick()

        composeTestRule.onNodeWithTag(MapScreenTestTags.ERROR_SUMMARY).assertIsDisplayed()
        composeTestRule.onNodeWithText("Select a statistical subrectangle").assertIsDisplayed()
    }

    @Test
    fun autocompleteScreenWithIncorrectlyFormattedInputShowsFormatErrorSummary() {
        composeTestRule.setContent {
            WizardTestTheme {
                MapScreen(state = stateWith(pendingGearUse()), onSubmit = {}, onBack = {})
            }
        }

        composeTestRule.onNodeWithTag(MapScreenTestTags.GRID_OTHER_ACTION).performClick()
        composeTestRule.onNodeWithTag("${MapScreenTestTags.RADIO_OPTION_PREFIX}_3").performClick()
        composeTestRule.onNodeWithTag(MapScreenTestTags.RADIO_SAVE_ACTION).performClick()

        composeTestRule.onNodeWithTag(MapScreenTestTags.AUTOCOMPLETE_FIELD).performTextInput("not-a-code")
        composeTestRule.onNodeWithTag(MapScreenTestTags.AUTOCOMPLETE_SAVE_ACTION).performClick()

        composeTestRule
            .onNodeWithText("Enter a statistical subrectangle in the correct format, for example 38E84")
            .assertIsDisplayed()
    }

    // --- Defensive fallback -----------------------------------------------------------------------------

    @Test
    fun noPendingGearShowsDefensiveFallbackMessage() {
        val draft =
            CatchRecordDraft(
                id = "draft-1",
                vesselId = "vessel-1",
                gearUses = emptyList(),
                modifiedAtEpochMillis = 0L,
                status = DraftStatus.Draft,
            )
        val state = CatchRecordFlowViewState(status = UiStatus.Content(draft))
        composeTestRule.setContent {
            WizardTestTheme {
                MapScreen(state = state, onSubmit = {}, onBack = {})
            }
        }

        composeTestRule.onNodeWithTag(MapScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
    }
}
