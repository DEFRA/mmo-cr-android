package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementField
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardTestTheme

/**
 * JVM/Robolectric port of (a focused subset of) the androidTest `GearScreensTest`'s "Gear search" section
 * — see the top-level package doc on [GearSpeciesChecklistScreenTests] for why a
 * `testDebugUnitTest`-reachable equivalent is needed for the Kover/SonarCloud coverage gate.
 */
@RunWith(RobolectricTestRunner::class)
class GearSearchScreenTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val meshSizeField =
        GearMeasurementField(key = "mesh_size_mm", label = "Mesh size (mm)", type = GearMeasurementFieldType.Integer)
    private val seineNets =
        GearType(id = "gear-seine-nets", name = "Seine nets (not specified)", measurementFields = listOf(meshSizeField))
    private val bottomOtterTrawls =
        GearType(
            id = "gear-bottom-otter-trawls-tb",
            name = "Bottom otter trawls (TB)",
            measurementFields = listOf(meshSizeField),
        )
    private val gearTypes = listOf(seineNets, bottomOtterTrawls)

    private fun sampleDraft(gearUses: List<GearUse>) =
        CatchRecordDraft(
            id = "draft-1",
            vesselId = "vessel-1",
            gearUses = gearUses,
            modifiedAtEpochMillis = 1L,
            status = DraftStatus.Draft,
        )

    @Test
    fun gearSearchGatesSuggestionsAndSubmitsSelectedGearTypeId() {
        var submittedId: String? = null
        composeTestRule.setContent {
            MmoTheme {
                GearSearchScreenContent(gearTypes = gearTypes, onSubmit = { submittedId = it })
            }
        }

        composeTestRule.onAllNodesWithTag("${GearSearchScreenTestTags.SUGGESTION_PREFIX}_0").assertCountEquals(0)
        composeTestRule.onNodeWithTag(GearSearchScreenTestTags.AUTOCOMPLETE_FIELD).performTextInput("Se")
        composeTestRule.onAllNodesWithTag("${GearSearchScreenTestTags.SUGGESTION_PREFIX}_0").assertCountEquals(1)
        composeTestRule.onNodeWithTag("${GearSearchScreenTestTags.SUGGESTION_PREFIX}_0").performClick()
        composeTestRule.onNodeWithTag(GearSearchScreenTestTags.SAVE_ACTION).performClick()
        assertEquals("gear-seine-nets", submittedId)
    }

    @Test
    fun gearSearchScreenShowsWhatGearDidYouUseHeadingWhenDraftHasNoGearYet() {
        val draft = sampleDraft(emptyList())
        val state = CatchRecordFlowViewState(status = UiStatus.Content(draft), gearTypes = gearTypes)
        composeTestRule.setContent {
            WizardTestTheme {
                GearSearchScreen(state = state, onSubmit = {}, onBack = {})
            }
        }

        composeTestRule.onNodeWithText("What gear did you use?").assertIsDisplayed()
    }

    @Test
    fun gearSearchScreenShowsAddGearToYourListHeadingWhenAddingAnotherGear() {
        val gearUse = GearUse(id = "gear-use-1", gearTypeId = seineNets.id, statisticalSubRectangleCode = null)
        val draft = sampleDraft(listOf(gearUse))
        val state = CatchRecordFlowViewState(status = UiStatus.Content(draft), gearTypes = gearTypes)
        composeTestRule.setContent {
            WizardTestTheme {
                GearSearchScreen(state = state, onSubmit = {}, onBack = {})
            }
        }

        composeTestRule.onNodeWithText("Add gear to your list").assertIsDisplayed()
    }

    @Test
    fun gearSearchWithNoSelectionShowsErrorAndDoesNotSubmit() {
        var submittedId: String? = null
        composeTestRule.setContent {
            MmoTheme {
                GearSearchScreenContent(gearTypes = gearTypes, onSubmit = { submittedId = it })
            }
        }

        composeTestRule.onNodeWithTag(GearSearchScreenTestTags.SAVE_ACTION).performClick()
        composeTestRule.onNodeWithTag(GearSearchScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
        assertNull(submittedId)
    }

    @Test
    fun outerStateErrorShowsRetryableWizardErrorAndInvokesOnRetry() {
        val state = CatchRecordFlowViewState(status = UiStatus.Error(message = "Could not load gear", isRetryable = true))
        var retried = false
        composeTestRule.setContent {
            WizardTestTheme {
                GearSearchScreen(state = state, onSubmit = {}, onBack = {}, onRetry = { retried = true })
            }
        }

        composeTestRule.onNodeWithTag(GearSearchScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
        composeTestRule.onNodeWithTag("${GearSearchScreenTestTags.ERROR_MESSAGE}_retry_action").performClick()
        assertEquals(true, retried)
    }

    @Test
    fun outerStateLoadingShowsLoadingIndicatorNotContent() {
        val state = CatchRecordFlowViewState(status = UiStatus.Loading)
        composeTestRule.setContent {
            WizardTestTheme {
                GearSearchScreen(state = state, onSubmit = {}, onBack = {})
            }
        }

        composeTestRule.onNodeWithTag(GearSearchScreenTestTags.AUTOCOMPLETE_FIELD).assertDoesNotExist()
    }
}
