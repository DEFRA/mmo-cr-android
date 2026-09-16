package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.submission

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.NotLandedSpeciesEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelection
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelectionMode
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SpeciesWeightEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementField
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Vessel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardStep

/**
 * Phase 8, screen 2 — the "check your answers" review screen. Only the pure, viewModel-free
 * [CheckYourAnswersScreenContent] is exercised here (see the `Screen`/`ScreenContent` split noted on
 * [LateSubmissionWarningScreenTest]).
 */
class CheckYourAnswersScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val vessel = Vessel(id = "vessel-1", name = "The Providence")
    private val departurePort = Port(id = "port-1", name = "Newlyn", statisticalAreaId = "area-1")
    private val returnPort = Port(id = "port-2", name = "Plymouth", statisticalAreaId = "area-1")
    private val seineNet =
        GearType(
            id = "gear-seine",
            name = "Seine nets",
            measurementFields =
                listOf(
                    GearMeasurementField(
                        key = "mesh_size_mm",
                        label = "Mesh size",
                        type = GearMeasurementFieldType.Integer,
                        unit = "mm",
                    ),
                ),
        )
    private val cod = Species(id = "species-cod", name = "Cod", faoCode = "COD")

    private fun draft() =
        CatchRecordDraft(
            id = "draft-1",
            vesselId = vessel.id,
            isTripToday = false,
            departureDate = DmyDate(1, 6, 2024),
            returnDate = DmyDate(3, 6, 2024),
            departurePort = PortSelection(departurePort.id, PortSelectionMode.FirstTime),
            returnPort = PortSelection(returnPort.id, PortSelectionMode.FirstTime),
            gearUses =
                listOf(
                    GearUse(
                        id = "gear-use-1",
                        gearTypeId = seineNet.id,
                        statisticalSubRectangleCode = "38E9",
                        confirmedUsedOnTrip = true,
                        numberOfShots = 4,
                        measurements = mapOf("mesh_size_mm" to MeasurementValue.Numeric(50.0, "mm")),
                        speciesWeights =
                            listOf(
                                SpeciesWeightEntry(
                                    id = "sw-1",
                                    speciesId = cod.id,
                                    weightAboveMinimumSizeKg = 12.5,
                                    confirmedCaught = true,
                                ),
                            ),
                    ),
                ),
            notLandedStraightAway = true,
            notLandedSpeciesEntries =
                listOf(
                    NotLandedSpeciesEntry(speciesId = cod.id, weightAboveMinimumSizeKeptOnboardKg = 3.0),
                ),
            modifiedAtEpochMillis = 0L,
        )

    private fun setContent(
        onChangeRow: (WizardStep) -> Unit = {},
        onSubmit: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            MmoTheme {
                CheckYourAnswersScreenContent(
                    draft = draft(),
                    vessels = listOf(vessel),
                    ports = listOf(departurePort, returnPort),
                    gearTypes = listOf(seineNet),
                    species = listOf(cod),
                    onChangeRow = onChangeRow,
                    onSubmit = onSubmit,
                    // Mirrors CatchRecordWizardScaffold's own verticalScroll: in production this screen's
                    // content is always a child of that already-scrollable scaffold, but exercising the
                    // pure *ScreenContent composable in isolation (per this codebase's Screen/ScreenContent
                    // test split) needs its own scroll container so every section remains reachable via
                    // performScrollTo(), exactly as a real device would let the user scroll to see it.
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }
    }

    @Test
    fun everySectionAndItsValuesAreDisplayed() {
        setContent()

        composeTestRule.onNodeWithText("The Providence").assertIsDisplayed()
        composeTestRule.onNodeWithText("Newlyn").assertIsDisplayed()
        composeTestRule.onNodeWithText("Plymouth").assertIsDisplayed()
        composeTestRule.onNodeWithText("38E9").assertIsDisplayed()
        composeTestRule.onNodeWithText("4").assertIsDisplayed()
        composeTestRule.onNodeWithText("50 mm").assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText("Cod")
            .onFirst()
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("12.5 kg").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Yes").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("3 kg").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun declarationPanelAndSubmitActionAreDisplayed() {
        setContent()

        composeTestRule
            .onNodeWithTag(
                CheckYourAnswersScreenTestTags.DECLARATION_PANEL,
            ).performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                "By submitting this record, you agree that the information you've given is complete and correct.",
            ).performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(
                CheckYourAnswersScreenTestTags.SUBMIT_ACTION,
            ).performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun tappingSubmitInvokesCallback() {
        var submitted = false
        setContent(onSubmit = { submitted = true })

        composeTestRule.onNodeWithTag(CheckYourAnswersScreenTestTags.SUBMIT_ACTION).performScrollTo().performClick()

        assertTrue(submitted)
    }

    @Test
    fun tappingAChangeLinkNavigatesToItsWizardStep() {
        var changedTo: WizardStep? = null
        setContent(onChangeRow = { changedTo = it })

        composeTestRule
            .onNodeWithTag("${CheckYourAnswersScreenTestTags.CHANGE_ACTION_PREFIX}_${CheckYourAnswersFieldKind.Vessel}")
            .performClick()

        assertEquals(WizardStep.VesselSelection, changedTo)
    }

    @Test
    fun everyChangeLinkHasADistinctAccessibleLabel() {
        setContent()

        // Every repeated "Change" link's own TalkBack label must name which row it changes (WCAG 2.2 AA —
        // not "Change, Change, Change..." for every row) — see GdsLinkAction's accessibleLabel parameter.
        composeTestRule.onNodeWithContentDescription("Change Vessel").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Change Departure port").assertIsDisplayed()
    }
}
