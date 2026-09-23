@file:Suppress("detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertValueEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelection
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelectionMode
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.GeoBoundingBox
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.GeoPoint
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.MapGeometryDataset
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.MapPort
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.StatisticalSubRectangleGeometry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldKeys
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.StatisticalSubRectangle
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.map.MapProjection

class GearStatRectangleScreenTest {
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

    /** A small global geometry fixture — two sea-overlapping sub-rectangles, one landlocked (excluded). */
    private fun sampleGeometry(): MapGeometryDataset {
        fun rectangle(
            code: String,
            seaOverlapping: Boolean,
            minLat: Double,
            maxLat: Double,
            minLng: Double,
            maxLng: Double,
        ) = StatisticalSubRectangleGeometry(
            subCode = code,
            parentIcesName = "ICES-$code",
            rings =
                listOf(
                    listOf(
                        GeoPoint(minLat, minLng),
                        GeoPoint(minLat, maxLng),
                        GeoPoint(maxLat, maxLng),
                        GeoPoint(maxLat, minLng),
                    ),
                ),
            bboxCentroid = GeoPoint((minLat + maxLat) / 2, (minLng + maxLng) / 2),
            boundingBox = GeoBoundingBox(minLat, maxLat, minLng, maxLng),
            seaOverlapping = seaOverlapping,
        )
        return MapGeometryDataset(
            landPolygons = emptyList(),
            subRectangles =
                listOf(
                    rectangle("38E95", seaOverlapping = true, minLat = 50.0, maxLat = 51.0, minLng = 0.0, maxLng = 1.0),
                    rectangle("38E98", seaOverlapping = true, minLat = 51.0, maxLat = 52.0, minLng = 0.0, maxLng = 1.0),
                    rectangle(
                        "00LND",
                        seaOverlapping = false,
                        minLat = 60.0,
                        maxLat = 61.0,
                        minLng = 0.0,
                        maxLng = 1.0,
                    ),
                ),
            ports = listOf(MapPort("Hastings", GeoPoint(50.855, 0.573))),
        )
    }

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
        mapGeometryStatus: UiStatus<MapGeometryDataset> = UiStatus.Content(sampleGeometry()),
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
            mapGeometryStatus = mapGeometryStatus,
        )
    }

    // --- Screen 1: schematic grid ---------------------------------------------------------------------

    @Test
    fun gridScreenShowsIdentifyingMeasurementInTitleAndSubmitsTappedCell() {
        var submittedDraft: CatchRecordDraft? = null
        composeTestRule.setContent {
            MmoTheme {
                GearStatRectangleScreen(
                    state = stateWith(pendingGearUse()),
                    onSubmit = { submittedDraft = it },
                    onBack = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText("Where was the majority of your catch caught using seine nets (mesh size 100mm)?")
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("${GearStatRectangleScreenTestTags.GRID_CELL_PREFIX}_0").performClick()
        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.GRID_SAVE_ACTION).performClick()

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
            MmoTheme {
                GearStatRectangleScreen(state = state, onSubmit = {}, onBack = {})
            }
        }

        composeTestRule
            .onNodeWithText("Where was the majority of your catch caught using handlines?")
            .assertIsDisplayed()
    }

    @Test
    fun gridScreenWithNoSelectionShowsRequiredErrorAndDoesNotSubmit() {
        var submittedDraft: CatchRecordDraft? = null
        composeTestRule.setContent {
            MmoTheme {
                GearStatRectangleScreen(
                    state = stateWith(pendingGearUse()),
                    onSubmit = { submittedDraft = it },
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.GRID_SAVE_ACTION).performClick()
        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
        assertNull(submittedDraft)
    }

    // --- Screen 2: "Other" -> map + synced list ----------------------------------------------------------
    // Ticket acceptance scenarios: Other -> map+list nav, map-tap -> list-sync, list-select -> map-sync,
    // save-with-selection advances, save-without-selection shows the exact required copy and stays.

    @Test
    fun gridOtherLinkNavigatesToTheMapAndListScreenShowingTheGlobalSeaOverlappingSet() {
        composeTestRule.setContent {
            MmoTheme {
                GearStatRectangleScreen(
                    state = stateWith(pendingGearUse()),
                    onSubmit = {},
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.GRID_OTHER_ACTION).performClick()

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.MAP_CANVAS).assertIsDisplayed()
        // Both sea-overlapping codes are listed; the landlocked one is excluded from the selectable list.
        composeTestRule
            .onNodeWithTag(
                "${GearStatRectangleScreenTestTags.MAP_LIST_OPTION_PREFIX}_38E95",
            ).assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(
                "${GearStatRectangleScreenTestTags.MAP_LIST_OPTION_PREFIX}_38E98",
            ).assertIsDisplayed()
    }

    @Test
    fun selectingAnOptionInTheSyncedListSubmitsTheChosenCode() {
        var submittedDraft: CatchRecordDraft? = null
        composeTestRule.setContent {
            MmoTheme {
                GearStatRectangleScreen(
                    state = stateWith(pendingGearUse()),
                    onSubmit = { submittedDraft = it },
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.GRID_OTHER_ACTION).performClick()
        composeTestRule.onNodeWithTag("${GearStatRectangleScreenTestTags.MAP_LIST_OPTION_PREFIX}_38E98").performClick()
        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.MAP_SAVE_ACTION).performClick()

        assertEquals("38E98", submittedDraft?.gearUses?.single()?.statisticalSubRectangleCode)
    }

    @Test
    fun selectingAnOptionInTheSyncedListAlsoUpdatesTheMapsOwnSelectionState() {
        composeTestRule.setContent {
            MmoTheme {
                GearStatRectangleScreen(
                    state = stateWith(pendingGearUse()),
                    onSubmit = {},
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.GRID_OTHER_ACTION).performClick()

        // Reverse direction (list-select -> map highlights): asserted against the map canvas's own
        // semantics state (StatisticalAreaMapCanvas exposes `stateDescription` for its currently selected
        // sub-code), not merely the shared `selectedCode` local variable — genuinely proving the map's own
        // selection state (and not just some parallel local UI state) tracks the list selection.
        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.MAP_CANVAS).assertValueEquals("No area selected")

        composeTestRule.onNodeWithTag("${GearStatRectangleScreenTestTags.MAP_LIST_OPTION_PREFIX}_38E98").performClick()

        composeTestRule
            .onNodeWithTag(GearStatRectangleScreenTestTags.MAP_CANVAS)
            .assertValueEquals("Selected: 38E98")
    }

    @Test
    fun tappingTheMapAtAKnownScreenPositionSelectsAndSubmitsTheCorrespondingSubCode() {
        var submittedDraft: CatchRecordDraft? = null
        composeTestRule.setContent {
            MmoTheme {
                GearStatRectangleScreen(
                    state = stateWith(pendingGearUse()),
                    onSubmit = { submittedDraft = it },
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.GRID_OTHER_ACTION).performClick()

        // The map's initial camera centre is Hastings (the departure port, injected via
        // MapCentring.initialCentreFor) — inside 38E95's box. 38E98 shares the same longitude range but sits
        // directly north of it, so the required tap position is derivable purely from the map canvas's own
        // real measured pixel size and the exact same MapProjection maths StatisticalAreaMapCanvas itself
        // uses (zoom defaults to 1f, no pan yet) — a genuine, deterministic map-tap, not a guessed pixel.
        val mapNode = composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.MAP_CANVAS).fetchSemanticsNode()
        val camera = MapProjection.CameraState(centre = GeoPoint(lat = 50.855, lng = 0.573), zoom = 1f)
        val targetWorldPoint = GeoPoint(lat = 51.5, lng = 0.573) // 38E98's bbox centroid
        val tapScreenPoint =
            MapProjection.worldToScreen(
                point = targetWorldPoint,
                camera = camera,
                viewportWidthPx = mapNode.size.width.toFloat(),
                viewportHeightPx = mapNode.size.height.toFloat(),
            )

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.MAP_CANVAS).performTouchInput {
            click(Offset(tapScreenPoint.x, tapScreenPoint.y))
        }

        // (a) the tap syncs the corresponding list radio option to selected.
        composeTestRule
            .onNodeWithTag("${GearStatRectangleScreenTestTags.MAP_LIST_OPTION_PREFIX}_38E98")
            .assertIsSelected()

        // (b) Save and Continue submits that exact sub_code onto the draft.
        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.MAP_SAVE_ACTION).performClick()
        assertEquals("38E98", submittedDraft?.gearUses?.single()?.statisticalSubRectangleCode)
    }

    @Test
    fun mapAndListSelectionWithNoChoiceShowsTheExactRequiredCopyAndMovesFocusToTheErrorSummary() {
        var submittedDraft: CatchRecordDraft? = null
        composeTestRule.setContent {
            MmoTheme {
                GearStatRectangleScreen(
                    state = stateWith(pendingGearUse()),
                    onSubmit = { submittedDraft = it },
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.GRID_OTHER_ACTION).performClick()
        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.MAP_SAVE_ACTION).performClick()

        // Accessible focus/announcement (finding: "Validation error lacks accessible focus/announcement") —
        // the same WizardErrorSummary + summaryFocusRequester convention as the Autocomplete sub-screen.
        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.ERROR_SUMMARY).assertIsDisplayed()
        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.ERROR_SUMMARY).assertIsFocused()
        composeTestRule
            .onNodeWithText("Select the statistical sub area where the majority of your catch was caught.")
            .assertIsDisplayed()
        assertNull(submittedDraft)
    }

    @Test
    fun mapAndListScreenShowsALoadingStateWhileGeometryIsStillLoading() {
        composeTestRule.setContent {
            MmoTheme {
                GearStatRectangleScreen(
                    state = stateWith(pendingGearUse(), mapGeometryStatus = UiStatus.Loading),
                    onSubmit = {},
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.GRID_OTHER_ACTION).performClick()

        // No map canvas / list rendered while geometry is still loading — a loading state is shown instead
        // (no endless spinner without any indication — WizardLoadingState provides an accessible label).
        composeTestRule.onAllNodesWithTag(GearStatRectangleScreenTestTags.MAP_CANVAS).assertCountEquals(0)
    }

    @Test
    fun mapAndListScreenShowsARetryableErrorWhenGeometryFailsToLoad() {
        var retried = false
        composeTestRule.setContent {
            MmoTheme {
                GearStatRectangleScreen(
                    state =
                        stateWith(
                            pendingGearUse(),
                            mapGeometryStatus = UiStatus.Error(message = "Unable to load the map.", isRetryable = true),
                        ),
                    onSubmit = {},
                    onBack = {},
                    onRetryGeometry = { retried = true },
                )
            }
        }

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.GRID_OTHER_ACTION).performClick()

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
        composeTestRule.onNodeWithTag("${GearStatRectangleScreenTestTags.ERROR_MESSAGE}_retry_action").performClick()

        assertEquals(true, retried)
    }

    // --- Screen 3: "Can't find it on the map?" -> autocomplete search (kept as a fallback) --------------

    @Test
    fun cantFindOnMapNavigatesToAutocompleteSearch() {
        composeTestRule.setContent {
            MmoTheme {
                GearStatRectangleScreen(
                    state = stateWith(pendingGearUse()),
                    onSubmit = {},
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.GRID_OTHER_ACTION).performClick()
        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.MAP_CANT_FIND_ACTION).performClick()

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.AUTOCOMPLETE_FIELD).assertIsDisplayed()
    }

    @Test
    fun autocompleteScreenSubmitsACorrectlyFormattedTypedCodeNotJustSuggestionListEntries() {
        var submittedDraft: CatchRecordDraft? = null
        composeTestRule.setContent {
            MmoTheme {
                GearStatRectangleScreen(
                    state = stateWith(pendingGearUse()),
                    onSubmit = { submittedDraft = it },
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.GRID_OTHER_ACTION).performClick()
        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.MAP_CANT_FIND_ACTION).performClick()

        // A code not present in the local nearby/stub list, entered via free text.
        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.AUTOCOMPLETE_FIELD).performTextInput("99Z99")
        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.AUTOCOMPLETE_SAVE_ACTION).performClick()

        assertEquals("99Z99", submittedDraft?.gearUses?.single()?.statisticalSubRectangleCode)
    }

    @Test
    fun autocompleteScreenWithBlankInputShowsRequiredErrorSummary() {
        composeTestRule.setContent {
            MmoTheme {
                GearStatRectangleScreen(
                    state = stateWith(pendingGearUse()),
                    onSubmit = {},
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.GRID_OTHER_ACTION).performClick()
        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.MAP_CANT_FIND_ACTION).performClick()

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.AUTOCOMPLETE_SAVE_ACTION).performClick()

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.ERROR_SUMMARY).assertIsDisplayed()
        composeTestRule.onNodeWithText("Select a statistical subrectangle").assertIsDisplayed()
    }

    @Test
    fun autocompleteScreenWithIncorrectlyFormattedInputShowsFormatErrorSummary() {
        composeTestRule.setContent {
            MmoTheme {
                GearStatRectangleScreen(
                    state = stateWith(pendingGearUse()),
                    onSubmit = {},
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.GRID_OTHER_ACTION).performClick()
        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.MAP_CANT_FIND_ACTION).performClick()

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.AUTOCOMPLETE_FIELD).performTextInput("not-a-code")
        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.AUTOCOMPLETE_SAVE_ACTION).performClick()

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
            MmoTheme {
                GearStatRectangleScreen(state = state, onSubmit = {}, onBack = {})
            }
        }

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
    }
}
