package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SpeciesWeightEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldKeys
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowEvent
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewModel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeCatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeCatchRecordSubmissionRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeCatchRecordSyncScheduler
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeNetworkConnectivityChecker
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeReferenceDataRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardStep
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardTestTheme

/** JVM coverage for species search paths that instrumented coverage cannot supply to Kover. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GearSpeciesSearchScreenTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val seineNets = GearType(id = "gear-seine-nets", name = "Seine nets (not specified)")
    private val cod = Species(id = "species-cod", name = "Atlantic cod (COD)", faoCode = "COD")
    private val haddock = Species(id = "species-haddock", name = "Haddock (HAD)", faoCode = "HAD")

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

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
    fun aRouteBoundCompletedGearIsShownAndSubmitsWithItsGearUseId() {
        val completeGear =
            gearUsePendingSpecies().copy(
                speciesWeights =
                    listOf(
                        SpeciesWeightEntry(
                            id = "species-1",
                            speciesId = cod.id,
                            confirmedCaught = true,
                        ),
                    ),
            )
        var submitted: Pair<String, String>? = null
        composeTestRule.setContent {
            WizardTestTheme {
                GearSpeciesSearchScreen(
                    state = stateWith(completeGear),
                    onSubmit = {},
                    onBack = {},
                    gearUseId = "gear-use-1",
                    onGearSubmit = { speciesId, gearUseId -> submitted = speciesId to gearUseId },
                )
            }
        }

        composeTestRule.onNodeWithTag(GearSpeciesSearchScreenTestTags.ERROR_MESSAGE).assertDoesNotExist()
        composeTestRule.onNodeWithTag(GearSpeciesSearchScreenTestTags.AUTOCOMPLETE_FIELD).performTextInput("Ha")
        composeTestRule.onNodeWithTag("${GearSpeciesSearchScreenTestTags.SUGGESTION_PREFIX}_0").performClick()
        composeTestRule.onNodeWithTag(GearSpeciesSearchScreenTestTags.SAVE_ACTION).performClick()

        assertEquals("species-haddock" to "gear-use-1", submitted)
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
        // LocalInspectionMode mirrors Preview so this composable runs without a Hilt component.
        composeTestRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                GearSpeciesSearchScreenPreview()
            }
        }

        composeTestRule
            .onNodeWithText("Which species did you catch with seine nets", substring = true)
            .assertIsDisplayed()
    }

    /** Covers add-mode species selection dispatch and navigation from the Hilt-backed wrapper. */
    @Test
    fun viewModelBackedScreenAddPathDispatchesSpeciesAddedToCurrentGearAndNavigates() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            repository.startDraft("vessel-achilles")
            repository.saveDraft(
                repository.getDraftById("draft-1").getOrThrow()!!.copy(gearUses = listOf(gearUsePendingSpecies())),
            )
            val viewModel =
                CatchRecordFlowViewModel(
                    repository,
                    FakeReferenceDataRepository(),
                    FakeCatchRecordSubmissionRepository(),
                    FakeNetworkConnectivityChecker(),
                    FakeCatchRecordSyncScheduler(),
                    { 0L },
                    { "gear-use-1" },
                    dispatcher,
                )
            var navigatedStep: WizardStep? = null

            composeTestRule.setContent {
                WizardTestTheme {
                    GearSpeciesSearchScreen(viewModel = viewModel, onNavigate = { navigatedStep = it }, onBack = {})
                }
            }
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft("draft-1"))
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag(GearSpeciesSearchScreenTestTags.AUTOCOMPLETE_FIELD).performTextInput("Co")
            composeTestRule.onNodeWithTag("${GearSpeciesSearchScreenTestTags.SUGGESTION_PREFIX}_0").performClick()
            composeTestRule.onNodeWithTag(GearSpeciesSearchScreenTestTags.SAVE_ACTION).performClick()
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            assertEquals(WizardStep.GearSpeciesChecklist, navigatedStep)
            val draft = (viewModel.state.value.status as UiStatus.Content<CatchRecordDraft>).value
            assertEquals(
                1,
                draft.gearUses
                    .single()
                    .speciesWeights.size,
            )
        }

    /** Covers edit-mode species selection dispatch for the explicit gear use. */
    @Test
    fun viewModelBackedScreenEditPathDispatchesSpeciesAddedToGearUseAndNavigates() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            repository.startDraft("vessel-achilles")
            repository.saveDraft(
                repository.getDraftById("draft-1").getOrThrow()!!.copy(gearUses = listOf(gearUsePendingSpecies())),
            )
            val viewModel =
                CatchRecordFlowViewModel(
                    repository,
                    FakeReferenceDataRepository(),
                    FakeCatchRecordSubmissionRepository(),
                    FakeNetworkConnectivityChecker(),
                    FakeCatchRecordSyncScheduler(),
                    { 0L },
                    { "gear-use-1" },
                    dispatcher,
                )
            var navigatedStep: WizardStep? = null

            composeTestRule.setContent {
                WizardTestTheme {
                    GearSpeciesSearchScreen(
                        viewModel = viewModel,
                        onNavigate = { navigatedStep = it },
                        onBack = {},
                        editGearUseId = "gear-use-1",
                    )
                }
            }
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft("draft-1"))
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag(GearSpeciesSearchScreenTestTags.AUTOCOMPLETE_FIELD).performTextInput("Pla")
            composeTestRule.onNodeWithTag("${GearSpeciesSearchScreenTestTags.SUGGESTION_PREFIX}_0").performClick()
            composeTestRule.onNodeWithTag(GearSpeciesSearchScreenTestTags.SAVE_ACTION).performClick()
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            assertEquals(WizardStep.GearSpeciesChecklist, navigatedStep)
            val draft = (viewModel.state.value.status as UiStatus.Content<CatchRecordDraft>).value
            assertEquals(
                "species-plaice",
                draft.gearUses
                    .single()
                    .speciesWeights
                    .single()
                    .speciesId,
            )
        }

    /**
     * JVM/Robolectric coverage for the Hilt-backed, stateful [GearSpeciesSearchScreen] wrapper's add path
     * when re-entered for an explicit, already-bound [gearUseId] (a Back-navigation resume) — the submit
     * must still target that gear use and navigate via [onNavigateForGear], not the plain [onNavigate].
     */
    @Test
    fun viewModelBackedScreenAddPathWithAnExplicitGearUseIdNavigatesForThatGear() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            repository.startDraft("vessel-achilles")
            repository.saveDraft(
                repository.getDraftById("draft-1").getOrThrow()!!.copy(gearUses = listOf(gearUsePendingSpecies())),
            )
            val viewModel =
                CatchRecordFlowViewModel(
                    repository,
                    FakeReferenceDataRepository(),
                    FakeCatchRecordSubmissionRepository(),
                    FakeNetworkConnectivityChecker(),
                    FakeCatchRecordSyncScheduler(),
                    { 0L },
                    { "gear-use-1" },
                    dispatcher,
                )
            var navigatedStep: WizardStep? = null
            var navigatedForGear: Pair<WizardStep, String>? = null

            composeTestRule.setContent {
                WizardTestTheme {
                    GearSpeciesSearchScreen(
                        viewModel = viewModel,
                        onNavigate = { navigatedStep = it },
                        onNavigateForGear = { step, gearUseId -> navigatedForGear = step to gearUseId },
                        onBack = {},
                        gearUseId = "gear-use-1",
                    )
                }
            }
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft("draft-1"))
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag(GearSpeciesSearchScreenTestTags.AUTOCOMPLETE_FIELD).performTextInput("Co")
            composeTestRule.onNodeWithTag("${GearSpeciesSearchScreenTestTags.SUGGESTION_PREFIX}_0").performClick()
            composeTestRule.onNodeWithTag(GearSpeciesSearchScreenTestTags.SAVE_ACTION).performClick()
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            assertEquals(WizardStep.GearSpeciesChecklist to "gear-use-1", navigatedForGear)
            assertNull(navigatedStep)
        }
}
