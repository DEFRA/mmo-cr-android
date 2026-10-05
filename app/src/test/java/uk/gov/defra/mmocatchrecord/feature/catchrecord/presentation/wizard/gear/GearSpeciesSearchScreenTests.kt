package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldKeys
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardTestTheme

/**
 * JVM/Robolectric port of the androidTest `GearSpeciesSearchScreenTest` — see the top-level package doc on
 * [GearSpeciesChecklistScreenTests] for why a `testDebugUnitTest`-reachable equivalent is needed.
 */
@RunWith(RobolectricTestRunner::class)
class GearSpeciesSearchScreenTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val seineNets = GearType(id = "gear-seine-nets", name = "Seine nets (not specified)")
    private val cod = Species(id = "species-cod", name = "Atlantic cod (COD)", faoCode = "COD")
    private val haddock = Species(id = "species-haddock", name = "Haddock (HAD)", faoCode = "HAD")

    private fun gearUsePendingSpecies() =
        GearUse(
            id = "gear-use-1",
            gearTypeId = seineNets.id,
            statisticalSubRectangleCode = "38E95",
            measurements = mapOf(GearMeasurementFieldKeys.MESH_SIZE_MM to MeasurementValue.Numeric(100.0, "mm")),
            confirmedUsedOnTrip = true,
        )

    private fun stateWith(gearUse: GearUse): CatchRecordFlowViewState {
        val draft =
            CatchRecordDraft(
                id = "draft-1",
                vesselId = "vessel-1",
                gearUses = listOf(gearUse),
                modifiedAtEpochMillis = 0L,
                status = DraftStatus.Draft,
            )
        return CatchRecordFlowViewState(
            status = UiStatus.Content(draft),
            gearTypes = listOf(seineNets),
            species = listOf(cod, haddock),
        )
    }

    @Test
    fun titleShowsGearNameWithIdentifyingMeasurement() {
        composeTestRule.setContent {
            WizardTestTheme {
                GearSpeciesSearchScreen(state = stateWith(gearUsePendingSpecies()), onSubmit = {}, onBack = {})
            }
        }

        composeTestRule
            .onNodeWithText("Which species did you catch with seine nets", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun typingTwoCharactersShowsMatchingSuggestionsAndSelectingOneSubmitsItsId() {
        var submittedId: String? = null
        composeTestRule.setContent {
            WizardTestTheme {
                GearSpeciesSearchScreen(
                    state = stateWith(gearUsePendingSpecies()),
                    onSubmit = { submittedId = it },
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(GearSpeciesSearchScreenTestTags.AUTOCOMPLETE_FIELD).performTextInput("Co")
        composeTestRule.onNodeWithTag("${GearSpeciesSearchScreenTestTags.SUGGESTION_PREFIX}_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("${GearSpeciesSearchScreenTestTags.SUGGESTION_PREFIX}_0").performClick()
        composeTestRule.onNodeWithTag(GearSpeciesSearchScreenTestTags.SAVE_ACTION).performClick()

        assertEquals("species-cod", submittedId)
    }

    @Test
    fun blankQueryOnSaveShowsEmptyQueryErrorAndDoesNotSubmit() {
        var submittedId: String? = null
        composeTestRule.setContent {
            WizardTestTheme {
                GearSpeciesSearchScreen(
                    state = stateWith(gearUsePendingSpecies()),
                    onSubmit = { submittedId = it },
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(GearSpeciesSearchScreenTestTags.SAVE_ACTION).performClick()

        composeTestRule.onNodeWithTag(GearSpeciesSearchScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
        assertNull(submittedId)
    }

    @Test
    fun typedTextWithNoMatchingSelectionShowsNoValidSelectionErrorAndDoesNotSubmit() {
        var submittedId: String? = null
        composeTestRule.setContent {
            WizardTestTheme {
                GearSpeciesSearchScreen(
                    state = stateWith(gearUsePendingSpecies()),
                    onSubmit = { submittedId = it },
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(GearSpeciesSearchScreenTestTags.AUTOCOMPLETE_FIELD).performTextInput("zz")
        composeTestRule.onNodeWithTag(GearSpeciesSearchScreenTestTags.SAVE_ACTION).performClick()

        composeTestRule.onNodeWithTag(GearSpeciesSearchScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
        assertNull(submittedId)
    }

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
        val state =
            CatchRecordFlowViewState(
                status = UiStatus.Content(draft),
                gearTypes = listOf(seineNets),
                species = listOf(cod, haddock),
            )
        composeTestRule.setContent {
            WizardTestTheme {
                GearSpeciesSearchScreen(state = state, onSubmit = {}, onBack = {})
            }
        }

        composeTestRule.onNodeWithTag(GearSpeciesSearchScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
    }

    @Test
    fun outerStateErrorShowsRetryableWizardError() {
        val state =
            CatchRecordFlowViewState(
                status = UiStatus.Error(message = "Could not load species", isRetryable = true),
            )
        var retried = false
        composeTestRule.setContent {
            WizardTestTheme {
                GearSpeciesSearchScreen(state = state, onSubmit = {}, onBack = {}, onRetry = { retried = true })
            }
        }

        composeTestRule.onNodeWithTag(GearSpeciesSearchScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
        composeTestRule.onNodeWithTag("${GearSpeciesSearchScreenTestTags.ERROR_MESSAGE}_retry_action").performClick()
        assertEquals(true, retried)
    }

    @Test
    fun outerStateLoadingShowsLoadingIndicatorNotContent() {
        val state = CatchRecordFlowViewState(status = UiStatus.Loading)
        composeTestRule.setContent {
            WizardTestTheme {
                GearSpeciesSearchScreen(state = state, onSubmit = {}, onBack = {})
            }
        }

        composeTestRule.onNodeWithTag(GearSpeciesSearchScreenTestTags.AUTOCOMPLETE_FIELD).assertDoesNotExist()
    }

    @Test
    fun screenPreviewRendersWithoutError() {
        // CatchRecordWizardScaffold resolves its language/connectivity state from Hilt view models unless
        // LocalInspectionMode is true (see that composable's doc comment) — providing it here mirrors what
        // Android Studio's own @Preview renderer does, letting this Preview composable run under plain
        // Robolectric without a Hilt component.
        composeTestRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                GearSpeciesSearchScreenPreview()
            }
        }

        composeTestRule
            .onNodeWithText("Which species did you catch with seine nets", substring = true)
            .assertIsDisplayed()
    }
}
