@file:Suppress("detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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

    // --- Screen 1: nearby map (default, no visible radio list) -----------------------------------------

    @Test
    fun nearbyMapScreenShowsIdentifyingMeasurementInTitleAndSubmitsTappedCell() {
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
        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.NEARBY_MAP_CANVAS).assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("${GearStatRectangleScreenTestTags.NEARBY_MAP_CELL_PREFIX}_38E95")
            .performClick()
        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.NEARBY_SAVE_ACTION).performClick()

        assertEquals("38E95", submittedDraft?.gearUses?.single()?.statisticalSubRectangleCode)
    }

    @Test
    fun nearbyMapScreenWithNoIdentifyingMeasurementOmitsParenthetical() {
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
    fun nearbyMapScreenShowsANonDefaultConfirmedGearTitleNotSeineNets() {
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
            MmoTheme {
                GearStatRectangleScreen(state = state, onSubmit = {}, onBack = {})
            }
        }

        composeTestRule
            .onNodeWithText("Where was the majority of your catch caught using bottom pair trawls (PTB)?")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Where was the majority of your catch caught using seine nets?")
            .assertDoesNotExist()
    }

    @Test
    fun nearbyMapScreenWithNoSelectionShowsRequiredErrorAndDoesNotSubmit() {
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

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.NEARBY_SAVE_ACTION).performClick()
        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.ERROR_SUMMARY).assertIsDisplayed()
        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.ERROR_SUMMARY).assertIsFocused()
        composeTestRule
            .onNodeWithText("Select the statistical sub area where the majority of your catch was caught.")
            .assertIsDisplayed()
        assertNull(submittedDraft)
    }

    @Test
    fun nearbyMapScreenShowsALoadingStateWhileGeometryIsStillLoading() {
        composeTestRule.setContent {
            MmoTheme {
                GearStatRectangleScreen(
                    state = stateWith(pendingGearUse(), mapGeometryStatus = UiStatus.Loading),
                    onSubmit = {},
                    onBack = {},
                )
            }
        }

        // No map canvas rendered while geometry is still loading — a loading state is shown instead (no
        // endless spinner without any indication — WizardLoadingState provides an accessible label).
        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.NEARBY_MAP_CANVAS).assertDoesNotExist()
    }

    @Test
    fun nearbyMapScreenShowsARetryableErrorWhenGeometryFailsToLoad() {
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

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
        composeTestRule.onNodeWithTag("${GearStatRectangleScreenTestTags.ERROR_MESSAGE}_retry_action").performClick()

        assertEquals(true, retried)
    }

    // --- Screen 2: "Other" -> free-text manual code entry (Autocomplete) --------------------------------
    // Ticket acceptance scenario: "Other" now jumps straight to the manual-entry search screen — there is
    // no intermediate visible map+list screen (removed; see GearStatRectangleScreen's doc comment).

    @Test
    fun otherLinkNavigatesDirectlyToAutocompleteSearch() {
        composeTestRule.setContent {
            MmoTheme {
                GearStatRectangleScreen(
                    state = stateWith(pendingGearUse()),
                    onSubmit = {},
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.NEARBY_OTHER_ACTION).performClick()

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

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.NEARBY_OTHER_ACTION).performClick()

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

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.NEARBY_OTHER_ACTION).performClick()

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

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.NEARBY_OTHER_ACTION).performClick()

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
