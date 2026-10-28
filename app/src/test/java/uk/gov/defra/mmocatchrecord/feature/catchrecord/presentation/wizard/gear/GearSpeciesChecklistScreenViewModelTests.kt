package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SpeciesWeightEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.SpeciesWeightPrecision
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowEvent
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewModel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeCatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeCatchRecordSubmissionRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeCatchRecordSyncScheduler
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeNetworkConnectivityChecker
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeReferenceDataRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardStep
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardTestTheme
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.nextWizardStepForDraft

/** Covers the Hilt-backed checklist wrapper separately to keep test classes below LargeClass limits. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GearSpeciesChecklistScreenViewModelTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(
        dispatcher: CoroutineDispatcher,
        repository: FakeCatchRecordDraftRepository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" }),
        referenceDataRepository: FakeReferenceDataRepository = FakeReferenceDataRepository(),
    ) = CatchRecordFlowViewModel(
        repository,
        referenceDataRepository,
        FakeCatchRecordSubmissionRepository(),
        FakeNetworkConnectivityChecker(),
        FakeCatchRecordSyncScheduler(),
        { 0L },
        { "gear-use-1" },
        dispatcher,
    )

    private val seineNets = GearType(id = "gear-seine-nets", name = "Seine nets (not specified)")

    private val cod =
        Species(
            id = "species-cod",
            name = "Atlantic cod (COD)",
            faoCode = "COD",
            weightPrecision = SpeciesWeightPrecision.OneDecimalPlace,
            weightAboveMinimumSizeMandatory = true,
        )

    private fun gearUseWithAddedSpecies(speciesIds: List<String>) =
        GearUse(
            id = "gear-use-1",
            gearTypeId = seineNets.id,
            statisticalSubRectangleCode = "38E95",
            speciesWeights =
                speciesIds.mapIndexed { index, id ->
                    SpeciesWeightEntry(id = "sw-$index", speciesId = id, confirmedCaught = false)
                },
            confirmedUsedOnTrip = true,
        )

    /** Covers create-mode submit dispatch and navigation from the Hilt-backed checklist wrapper. */
    @Test
    fun viewModelBackedScreenCreatePathSubmitsWeightsAndNavigatesToNextStep() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            repository.startDraft("vessel-achilles")
            repository.saveDraft(
                repository.getDraftById("draft-1").getOrThrow()!!.copy(
                    gearUses = listOf(gearUseWithAddedSpecies(listOf(cod.id))),
                ),
            )
            val viewModel = buildViewModel(dispatcher, repository)
            var navigatedStep: WizardStep? = null

            composeTestRule.setContent {
                WizardTestTheme {
                    GearSpeciesChecklistScreen(viewModel = viewModel, onNavigate = { navigatedStep = it }, onBack = {})
                }
            }
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft("draft-1"))
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule
                .onNodeWithTag("${GearSpeciesChecklistScreenTestTags.CHECKBOX_PREFIX}_0")
                .performScrollTo()
                .performClick()
            composeTestRule
                .onNodeWithTag("${GearSpeciesChecklistScreenTestTags.ABOVE_MIN_FIELD_PREFIX}_${cod.id}")
                .performScrollTo()
                .performTextInput("5.5")
            composeTestRule
                .onNodeWithTag(
                    GearSpeciesChecklistScreenTestTags.SAVE_ACTION,
                ).performScrollTo()
                .performClick()
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            val draft = (viewModel.state.value.status as UiStatus.Content<CatchRecordDraft>).value
            assertEquals(nextWizardStepForDraft(draft), navigatedStep)
            assertTrue(
                draft.gearUses
                    .single()
                    .speciesWeights
                    .single { it.speciesId == cod.id }
                    .confirmedCaught,
            )
        }

    /** Covers edit-mode dispatch for the explicit gear use and navigation back to check your answers. */
    @Test
    fun viewModelBackedScreenEditPathDispatchesEditGearSpeciesWeightsAndNavigatesToCheckYourAnswers() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            repository.startDraft("vessel-achilles")
            repository.saveDraft(
                repository.getDraftById("draft-1").getOrThrow()!!.copy(
                    gearUses = listOf(gearUseWithAddedSpecies(listOf(cod.id))),
                ),
            )
            val viewModel = buildViewModel(dispatcher, repository)
            var navigatedStep: WizardStep? = null

            composeTestRule.setContent {
                WizardTestTheme {
                    GearSpeciesChecklistScreen(
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

            composeTestRule
                .onNodeWithTag("${GearSpeciesChecklistScreenTestTags.CHECKBOX_PREFIX}_0")
                .performScrollTo()
                .performClick()
            composeTestRule
                .onNodeWithTag("${GearSpeciesChecklistScreenTestTags.ABOVE_MIN_FIELD_PREFIX}_${cod.id}")
                .performScrollTo()
                .performTextInput("5.5")
            composeTestRule
                .onNodeWithTag(
                    GearSpeciesChecklistScreenTestTags.SAVE_ACTION,
                ).performScrollTo()
                .performClick()
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            assertEquals(WizardStep.CheckYourAnswers, navigatedStep)
            val draft = (viewModel.state.value.status as UiStatus.Content<CatchRecordDraft>).value
            assertTrue(
                draft.gearUses
                    .single()
                    .speciesWeights
                    .single { it.speciesId == cod.id }
                    .confirmedCaught,
            )
        }

    @Test
    fun anOuterLoadErrorIsShownAsARetryableWizardErrorAndRecoversViaRetry() =
        runTest {
            val referenceData = FakeReferenceDataRepository().apply { failNextOperation = true }
            val viewModel =
                buildViewModel(
                    dispatcher = StandardTestDispatcher(testScheduler),
                    referenceDataRepository = referenceData,
                )

            composeTestRule.setContent {
                WizardTestTheme {
                    GearSpeciesChecklistScreen(viewModel = viewModel, onNavigate = {}, onBack = {})
                }
            }
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag(GearSpeciesChecklistScreenTestTags.CHECKLIST_ERROR).assertIsDisplayed()
            composeTestRule
                .onNodeWithTag("${GearSpeciesChecklistScreenTestTags.CHECKLIST_ERROR}_retry_action")
                .performClick()
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            assertEquals(UiStatus.Idle, viewModel.state.value.status)
        }

    @Test
    fun aDraftWithNoGearUsePendingSpeciesShowsTheDefensiveFallbackMessage() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            repository.startDraft("vessel-achilles")
            repository.saveDraft(repository.getDraftById("draft-1").getOrThrow()!!.copy(gearUses = emptyList()))
            val viewModel = buildViewModel(dispatcher, repository)

            composeTestRule.setContent {
                WizardTestTheme {
                    GearSpeciesChecklistScreen(viewModel = viewModel, onNavigate = {}, onBack = {})
                }
            }
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft("draft-1"))
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag(GearSpeciesChecklistScreenTestTags.CHECKLIST_ERROR).assertIsDisplayed()
        }
}
