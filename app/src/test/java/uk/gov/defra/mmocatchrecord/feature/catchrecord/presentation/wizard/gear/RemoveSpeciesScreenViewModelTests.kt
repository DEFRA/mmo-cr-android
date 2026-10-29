package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
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
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SpeciesWeightEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowEvent
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewModel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeCatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeCatchRecordSubmissionRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeCatchRecordSyncScheduler
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeNetworkConnectivityChecker
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeReferenceDataRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardTestTheme

/** Covers the Hilt-backed [RemoveSpeciesScreen] wrapper and its private state-driven branches —
 * mirrors [GearSpeciesChecklistScreenViewModelTests]'s approach for its sibling screen. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RemoveSpeciesScreenViewModelTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

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

    private fun gearUseWithAddedSpecies(speciesIds: List<String>) =
        GearUse(
            id = "gear-use-1",
            gearTypeId = "gear-seine-nets",
            statisticalSubRectangleCode = "38E95",
            speciesWeights =
                speciesIds.mapIndexed { index, id ->
                    SpeciesWeightEntry(id = "sw-$index", speciesId = id, confirmedCaught = true)
                },
            confirmedUsedOnTrip = true,
        )

    /** Covers the title's vessel-name lookup, the checkbox/delete happy path and the onBack + dispatch
     * wiring from the public, Hilt-backed entry point (lines not reachable from [RemoveSpeciesScreenContent]
     * tests alone). */
    @Test
    fun viewModelBackedScreenShowsTheVesselNameInTitleAndDeletesSelectedSpeciesThenNavigatesBack() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            repository.startDraft("vessel-achilles")
            repository.saveDraft(
                repository.getDraftById("draft-1").getOrThrow()!!.copy(
                    gearUses = listOf(gearUseWithAddedSpecies(listOf("species-cod", "species-plaice"))),
                ),
            )
            val viewModel = buildViewModel(dispatcher, repository)
            var backInvoked = false

            composeTestRule.setContent {
                WizardTestTheme {
                    RemoveSpeciesScreen(
                        viewModel = viewModel,
                        gearUseId = "gear-use-1",
                        onBack = { backInvoked = true },
                    )
                }
            }
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft("draft-1"))
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            val expectedTitle = context.getString(R.string.remove_species_title, "ACHILLES")
            composeTestRule.onNodeWithText(expectedTitle).assertIsDisplayed()

            composeTestRule.onNodeWithTag("${RemoveSpeciesScreenTestTags.CHECKBOX_PREFIX}_0").performClick()
            composeTestRule.onNodeWithTag(RemoveSpeciesScreenTestTags.CONTINUE_ACTION).performClick()
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            assertTrue(backInvoked)
            val draft = (viewModel.state.value.status as UiStatus.Content<CatchRecordDraft>).value
            assertEquals(
                listOf("species-plaice"),
                draft.gearUses
                    .single()
                    .speciesWeights
                    .map { it.speciesId },
            )
        }

    /** Defensive branch — mirrors the equivalent branch in [GearSpeciesChecklistScreen]: a requested
     * gear use that no longer exists on the draft shows the fallback message instead of crashing. */
    @Test
    fun aDraftWithNoMatchingGearUseShowsTheDefensiveFallbackMessage() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            repository.startDraft("vessel-achilles")
            repository.saveDraft(repository.getDraftById("draft-1").getOrThrow()!!.copy(gearUses = emptyList()))
            val viewModel = buildViewModel(dispatcher, repository)

            composeTestRule.setContent {
                WizardTestTheme {
                    RemoveSpeciesScreen(viewModel = viewModel, gearUseId = "gear-use-1", onBack = {})
                }
            }
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft("draft-1"))
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag(RemoveSpeciesScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
        }

    /** Covers the outer [UiStatus.Error] branch and its Retry control, as the equivalent checklist test
     * does for [GearSpeciesChecklistScreen]. */
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
                    RemoveSpeciesScreen(viewModel = viewModel, gearUseId = "gear-use-1", onBack = {})
                }
            }
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag(RemoveSpeciesScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
            composeTestRule
                .onNodeWithTag("${RemoveSpeciesScreenTestTags.ERROR_MESSAGE}_retry_action")
                .performClick()
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            assertEquals(UiStatus.Idle, viewModel.state.value.status)
        }

    /** Covers the combined [UiStatus.Idle]/[UiStatus.Loading] `when` branch, rendered before any
     * [CatchRecordFlowEvent] is dispatched — the only state from which neither the error nor the
     * content branch is reachable. */
    @Test
    fun theInitialIdleStateRendersTheLoadingBranchWithNeitherContentNorError() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val viewModel = buildViewModel(dispatcher)

            composeTestRule.setContent {
                WizardTestTheme {
                    RemoveSpeciesScreen(viewModel = viewModel, gearUseId = "gear-use-1", onBack = {})
                }
            }
            composeTestRule.waitForIdle()

            assertEquals(UiStatus.Idle, viewModel.state.value.status)
            composeTestRule.onNodeWithTag(RemoveSpeciesScreenTestTags.ERROR_MESSAGE).assertDoesNotExist()
            composeTestRule.onNodeWithTag(RemoveSpeciesScreenTestTags.CONTINUE_ACTION).assertDoesNotExist()
        }

    /** Renders the screen's `@Preview` composable to cover it under the `testDevDebugUnitTest` gate,
     * mirroring the "preview renders without crashing" convention used across this codebase. */
    @Test
    fun previewRendersWithoutCrashing() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                RemoveSpeciesScreen_Preview()
            }
        }

        composeTestRule.onNodeWithTag(RemoveSpeciesScreenTestTags.SCREEN).assertIsDisplayed()
    }
}
