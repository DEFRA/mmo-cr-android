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
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowEvent
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewModel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeCatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeCatchRecordSubmissionRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeCatchRecordSyncScheduler
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeNetworkConnectivityChecker
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeReferenceDataRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardTestTheme

/** Covers the Hilt-backed [RemoveGearScreen] wrapper and its state-driven branches. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RemoveGearScreenViewModelTests {
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

    private fun gearUse(id: String) =
        GearUse(id = id, gearTypeId = "gear-seine-nets", statisticalSubRectangleCode = "38E95")

    @Test
    fun viewModelBackedScreenShowsTheVesselNameInTitleAndRemovesSelectedGearThenNavigatesBack() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            repository.startDraft("vessel-achilles")
            repository.saveDraft(
                repository.getDraftById("draft-1").getOrThrow()!!.copy(
                    gearUses = listOf(gearUse("gear-use-1"), gearUse("gear-use-2")),
                ),
            )
            val viewModel = buildViewModel(dispatcher, repository)
            var backInvoked = false

            composeTestRule.setContent {
                WizardTestTheme {
                    RemoveGearScreen(viewModel = viewModel, onBack = { backInvoked = true })
                }
            }
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft("draft-1"))
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule
                .onNodeWithText(context.getString(R.string.remove_gear_title, "ACHILLES"))
                .assertIsDisplayed()

            composeTestRule.onNodeWithTag("${RemoveGearScreenTestTags.CHECKBOX_PREFIX}_0").performClick()
            composeTestRule.onNodeWithTag(RemoveGearScreenTestTags.CONTINUE_ACTION).performClick()
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            assertTrue(backInvoked)
            val draft = (viewModel.state.value.status as UiStatus.Content<CatchRecordDraft>).value
            assertEquals(listOf("gear-use-2"), draft.gearUses.map { it.id })
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
                    RemoveGearScreen(viewModel = viewModel, onBack = {})
                }
            }
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag(RemoveGearScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
            composeTestRule
                .onNodeWithTag("${RemoveGearScreenTestTags.ERROR_MESSAGE}_retry_action")
                .performClick()
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            assertEquals(UiStatus.Idle, viewModel.state.value.status)
        }

    @Test
    fun theInitialIdleStateRendersTheLoadingBranchWithNeitherContentNorError() {
        val viewModel = buildViewModel(StandardTestDispatcher())

        composeTestRule.setContent {
            WizardTestTheme {
                RemoveGearScreen(viewModel = viewModel, onBack = {})
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(RemoveGearScreenTestTags.ERROR_MESSAGE).assertDoesNotExist()
        composeTestRule.onNodeWithTag(RemoveGearScreenTestTags.CONTINUE_ACTION).assertDoesNotExist()
    }

    @Test
    fun previewRendersWithoutCrashing() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                RemoveGearScreen_Preview()
            }
        }

        composeTestRule.onNodeWithTag(RemoveGearScreenTestTags.SCREEN).assertIsDisplayed()
    }
}
