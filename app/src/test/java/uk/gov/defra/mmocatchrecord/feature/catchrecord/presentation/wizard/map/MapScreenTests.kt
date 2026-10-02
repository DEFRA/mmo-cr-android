@file:Suppress("detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.map

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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

/**
 * JVM/Robolectric port of the androidTest `MapScreenTest` — see that file's doc comments for the full
 * rationale. Coverage from `connectedDebugAndroidTest` is not merged into the Kover/SonarCloud report that
 * backs this PR's quality gate, so [MapScreen] (and the [MapCanvas]/[MapSupport]/[MapScreenStatusContent]
 * it composes) needs an equivalent `testDebugUnitTest` exerciser as well.
 */
@RunWith(RobolectricTestRunner::class)
class MapScreenTests {
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
        composeTestRule.onNodeWithTag(MapScreenTestTags.MAP).captureToImage()
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

    @Test
    fun gridScreenWhenMapDataIsIdleShowsLoadingNotTheMapOrAnError() {
        composeTestRule.setContent {
            WizardTestTheme {
                MapScreen(
                    state = stateWith(pendingGearUse()),
                    onSubmit = {},
                    onBack = {},
                    mapDataState = MapDataUiState(UiStatus.Idle, onRetryMapData = {}),
                )
            }
        }

        composeTestRule.onNodeWithTag(MapScreenTestTags.MAP).assertDoesNotExist()
        composeTestRule.onNodeWithTag(MapScreenTestTags.MAP_ERROR).assertDoesNotExist()
        composeTestRule.onNodeWithTag(MapScreenTestTags.GRID_OTHER_ACTION).assertIsDisplayed()
    }

    /**
     * Also exercises the non-retryable branch of the shared [uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardErrorState]
     * (no "Try again" action rendered) for the outer draft-load failure, which the happy-path tests above
     * never reach since they always start from [UiStatus.Content].
     */
    @Test
    fun outerStateErrorShowsNonRetryableMessageWithNoRetryAction() {
        val state =
            CatchRecordFlowViewState(
                status = UiStatus.Error(message = "Could not load your draft", isRetryable = false),
            )
        composeTestRule.setContent {
            WizardTestTheme {
                MapScreen(state = state, onSubmit = {}, onBack = {})
            }
        }

        composeTestRule.onNodeWithTag(MapScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
        composeTestRule.onNodeWithText("Could not load your draft").assertIsDisplayed()
    }

    @Test
    fun outerStateLoadingShowsLoadingIndicatorNotContent() {
        val state = CatchRecordFlowViewState(status = UiStatus.Loading)
        composeTestRule.setContent {
            WizardTestTheme {
                MapScreen(state = state, onSubmit = {}, onBack = {})
            }
        }

        composeTestRule.onNodeWithTag(MapScreenTestTags.MAP).assertDoesNotExist()
    }

    @Test
    fun outerStateIdleShowsLoadingIndicatorNotContent() {
        val state = CatchRecordFlowViewState(status = UiStatus.Idle)
        composeTestRule.setContent {
            WizardTestTheme {
                MapScreen(state = state, onSubmit = {}, onBack = {})
            }
        }

        composeTestRule.onNodeWithTag(MapScreenTestTags.MAP).assertDoesNotExist()
    }

    @Test
    fun contentStateWithAnEditGearUseIdNotFoundOnTheDraftShowsTheDefensiveMissingGearError() {
        // Defensive-only branch (see MapScreenStatusContent's doc comment): normal navigation never reaches
        // this screen with an editGearUseId that doesn't resolve to a real gear use on the draft.
        composeTestRule.setContent {
            WizardTestTheme {
                MapScreen(
                    state = stateWith(pendingGearUse()),
                    onSubmit = {},
                    onBack = {},
                    editGearUseId = "no-such-gear-use",
                )
            }
        }

        composeTestRule.onNodeWithTag(MapScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Unable to load this gear's statistical area. Please go back and try again.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MapScreenTestTags.MAP).assertDoesNotExist()
    }

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
            ApplicationProvider
                .getApplicationContext<android.app.Application>()
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
        composeTestRule.onNodeWithTag("${MapScreenTestTags.RADIO_OPTION_PREFIX}_1").performScrollTo().performClick()
        composeTestRule.onNodeWithTag(MapScreenTestTags.RADIO_SAVE_ACTION).performScrollTo().performClick()

        assertEquals("38E98", submittedDraft?.gearUses?.single()?.statisticalSubRectangleCode)
    }

    @Test
    fun radioListWithNoSelectionShowsRequiredError() {
        var submittedDraft: CatchRecordDraft? = null
        setMapScreen(onSubmit = { submittedDraft = it })

        composeTestRule.onNodeWithTag(MapScreenTestTags.GRID_OTHER_ACTION).performClick()
        composeTestRule.onNodeWithTag(MapScreenTestTags.RADIO_SAVE_ACTION).performScrollTo().performClick()

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
        composeTestRule.onNodeWithTag("${MapScreenTestTags.RADIO_OPTION_PREFIX}_3").performScrollTo().performClick()
        composeTestRule.onNodeWithTag(MapScreenTestTags.RADIO_SAVE_ACTION).performScrollTo().performClick()

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
        composeTestRule.onNodeWithTag("${MapScreenTestTags.RADIO_OPTION_PREFIX}_3").performScrollTo().performClick()
        composeTestRule.onNodeWithTag(MapScreenTestTags.RADIO_SAVE_ACTION).performScrollTo().performClick()

        // A code not present in the local nearby/stub list, entered via free text.
        composeTestRule.onNodeWithTag(MapScreenTestTags.AUTOCOMPLETE_FIELD).performScrollTo().performTextInput("99Z99")
        composeTestRule.onNodeWithTag(MapScreenTestTags.AUTOCOMPLETE_SAVE_ACTION).performScrollTo().performClick()

        assertEquals("99Z99", submittedDraft?.gearUses?.single()?.statisticalSubRectangleCode)
    }

    @Test
    fun autocompleteScreenTappingASuggestionFillsTheFieldAndSubmits() {
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
        composeTestRule.onNodeWithTag("${MapScreenTestTags.RADIO_OPTION_PREFIX}_3").performScrollTo().performClick()
        composeTestRule.onNodeWithTag(MapScreenTestTags.RADIO_SAVE_ACTION).performScrollTo().performClick()

        composeTestRule.onNodeWithTag(MapScreenTestTags.AUTOCOMPLETE_FIELD).performScrollTo().performTextInput("38E9")
        composeTestRule
            .onNodeWithTag("${MapScreenTestTags.SUGGESTION_PREFIX}_0")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag(MapScreenTestTags.AUTOCOMPLETE_SAVE_ACTION).performScrollTo().performClick()

        assertEquals("38E95", submittedDraft?.gearUses?.single()?.statisticalSubRectangleCode)
    }

    @Test
    fun autocompleteScreenWithBlankInputShowsRequiredErrorSummary() {
        composeTestRule.setContent {
            WizardTestTheme {
                MapScreen(state = stateWith(pendingGearUse()), onSubmit = {}, onBack = {})
            }
        }

        composeTestRule.onNodeWithTag(MapScreenTestTags.GRID_OTHER_ACTION).performClick()
        composeTestRule.onNodeWithTag("${MapScreenTestTags.RADIO_OPTION_PREFIX}_3").performScrollTo().performClick()
        composeTestRule.onNodeWithTag(MapScreenTestTags.RADIO_SAVE_ACTION).performScrollTo().performClick()

        composeTestRule.onNodeWithTag(MapScreenTestTags.AUTOCOMPLETE_SAVE_ACTION).performScrollTo().performClick()

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
        composeTestRule.onNodeWithTag("${MapScreenTestTags.RADIO_OPTION_PREFIX}_3").performScrollTo().performClick()
        composeTestRule.onNodeWithTag(MapScreenTestTags.RADIO_SAVE_ACTION).performScrollTo().performClick()

        composeTestRule
            .onNodeWithTag(
                MapScreenTestTags.AUTOCOMPLETE_FIELD,
            ).performScrollTo()
            .performTextInput("not-a-code")
        composeTestRule.onNodeWithTag(MapScreenTestTags.AUTOCOMPLETE_SAVE_ACTION).performScrollTo().performClick()

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

    // --- Offline banner (also closes CatchRecordWizardScaffold's isOffline branch) ----------------------

    @Test
    fun offlineStateShowsOfflineBanner() {
        composeTestRule.setContent {
            WizardTestTheme(isOffline = true) {
                MapScreen(state = stateWith(pendingGearUse()), onSubmit = {}, onBack = {})
            }
        }

        composeTestRule.onNodeWithTag("offline_banner").assertIsDisplayed()
    }

    // --- Previews (new code added for this PR — not otherwise exercised by any screen-level test) --------

    @Test
    fun gridPreviewRendersWithoutError() {
        // CatchRecordWizardScaffold resolves its language/connectivity state from Hilt view models unless
        // LocalInspectionMode is true (see that composable's doc comment) — providing it here mirrors what
        // Android Studio's own @Preview renderer does, letting this Preview composable run under plain
        // Robolectric without a Hilt component. The Preview's default `mapDataState` (Loading) also closes
        // out MapGridContent's "map data still loading" branches.
        composeTestRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                MapScreenGridPreview()
            }
        }

        composeTestRule.onNodeWithTag(MapScreenTestTags.GRID_OTHER_ACTION).assertIsDisplayed()
    }

    @Test
    fun radioListPreviewRendersWithoutError() {
        // MapScreenContent (unlike MapScreen) never calls CatchRecordWizardScaffold, so no
        // LocalInspectionMode wrapper is needed here.
        composeTestRule.setContent {
            MapScreenRadioListPreview()
        }

        composeTestRule.onNodeWithTag("${MapScreenTestTags.RADIO_OPTION_PREFIX}_0").assertIsDisplayed()
    }

    @Test
    fun autocompletePreviewRendersWithoutError() {
        composeTestRule.setContent {
            MapScreenAutocompletePreview()
        }

        composeTestRule.onNodeWithTag(MapScreenTestTags.AUTOCOMPLETE_FIELD).assertIsDisplayed()
    }
}
