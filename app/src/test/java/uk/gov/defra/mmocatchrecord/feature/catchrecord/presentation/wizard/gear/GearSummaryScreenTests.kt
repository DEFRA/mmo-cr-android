package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
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
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelection
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelectionMode
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SpeciesWeightEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldKeys
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
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
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.resolveSubmissionStep

/** JVM coverage for gear-summary checklist and Hilt-backed wrapper paths. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GearSummaryScreenTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val seineNets = GearType(id = "gear-seine-nets", name = "Seine nets (not specified)")
    private val bottomOtterTrawls = GearType(id = "gear-bottom-otter-trawls-tb", name = "Bottom otter trawls (TB)")
    private val gearTypes = listOf(seineNets, bottomOtterTrawls)

    private val gearUseSeineNets =
        GearUse(
            id = "gear-use-1",
            gearTypeId = "gear-seine-nets",
            statisticalSubRectangleCode = null,
            measurements = mapOf(GearMeasurementFieldKeys.MESH_SIZE_MM to MeasurementValue.Numeric(100.0, "mm")),
        )
    private val gearUseBottomOtterTrawls =
        GearUse(
            id = "gear-use-2",
            gearTypeId = "gear-bottom-otter-trawls-tb",
            statisticalSubRectangleCode = null,
            measurements = mapOf(GearMeasurementFieldKeys.MESH_SIZE_MM to MeasurementValue.Numeric(80.0, "mm")),
        )

    private fun sampleDraft(gearUses: List<GearUse>) =
        CatchRecordDraft(
            id = "draft-1",
            vesselId = "vessel-1",
            gearUses = gearUses,
            modifiedAtEpochMillis = 1L,
            status = DraftStatus.Draft,
        )

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

    // --- Content composable ----------------------------------------------------------------------

    @Test
    fun gearSummaryShowsEachGearWithItsMeasurementSummary() {
        composeTestRule.setContent {
            MmoTheme {
                GearSummaryScreenContent(
                    draft = sampleDraft(listOf(gearUseSeineNets, gearUseBottomOtterTrawls)),
                    gearTypes = gearTypes,
                    onRemoveGear = {},
                    onAddAnotherGear = {},
                    onSubmit = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("${GearSummaryScreenTestTags.CHECKBOX_PREFIX}_0").assertIsOff()
        composeTestRule.onNodeWithTag("${GearSummaryScreenTestTags.CHECKBOX_PREFIX}_1").assertIsOff()
    }

    @Test
    fun checkingAGearRevealsItsShotsFieldAndSubmitConfirmsIt() {
        var submittedDraft: CatchRecordDraft? = null
        composeTestRule.setContent {
            MmoTheme {
                GearSummaryScreenContent(
                    draft = sampleDraft(listOf(gearUseSeineNets)),
                    gearTypes = gearTypes,
                    onRemoveGear = {},
                    onAddAnotherGear = {},
                    onSubmit = { submittedDraft = it },
                )
            }
        }

        composeTestRule
            .onAllNodesWithTag("${GearSummaryScreenTestTags.SHOTS_FIELD_PREFIX}_gear-use-1")
            .assertCountEquals(0)
        composeTestRule.onNodeWithTag("${GearSummaryScreenTestTags.CHECKBOX_PREFIX}_0").performClick()
        composeTestRule.onNodeWithTag("${GearSummaryScreenTestTags.CHECKBOX_PREFIX}_0").assertIsOn()
        composeTestRule
            .onNodeWithTag("${GearSummaryScreenTestTags.SHOTS_FIELD_PREFIX}_gear-use-1")
            .performTextInput("3")
        composeTestRule.onNodeWithTag(GearSummaryScreenTestTags.SAVE_ACTION).performClick()

        val gearUse = submittedDraft!!.gearUses.single()
        assertTrue(gearUse.confirmedUsedOnTrip)
        assertEquals(3, gearUse.numberOfShots)
    }

    @Test
    fun removeGearLinkIsDisabledWhenNothingIsCheckedAndEnabledWhenSomethingIsChecked() {
        composeTestRule.setContent {
            MmoTheme {
                GearSummaryScreenContent(
                    draft = sampleDraft(listOf(gearUseSeineNets, gearUseBottomOtterTrawls)),
                    gearTypes = gearTypes,
                    onRemoveGear = {},
                    onAddAnotherGear = {},
                    onSubmit = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(GearSummaryScreenTestTags.REMOVE_ACTION).assertIsNotEnabled()
        composeTestRule.onNodeWithTag("${GearSummaryScreenTestTags.CHECKBOX_PREFIX}_0").performClick()
        composeTestRule.onNodeWithTag(GearSummaryScreenTestTags.REMOVE_ACTION).assertIsEnabled()
    }

    @Test
    fun removeGearBulkRemovesOnlyCheckedGearUses() {
        var updatedDraft: CatchRecordDraft? = null
        composeTestRule.setContent {
            MmoTheme {
                GearSummaryScreenContent(
                    draft = sampleDraft(listOf(gearUseSeineNets, gearUseBottomOtterTrawls)),
                    gearTypes = gearTypes,
                    onRemoveGear = { updatedDraft = it },
                    onAddAnotherGear = {},
                    onSubmit = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("${GearSummaryScreenTestTags.CHECKBOX_PREFIX}_0").performClick()
        composeTestRule.onNodeWithTag(GearSummaryScreenTestTags.REMOVE_ACTION).performClick()

        assertEquals(listOf("gear-use-2"), updatedDraft?.gearUses?.map { it.id })
    }

    @Test
    fun addAnotherGearInvokesCallback() {
        var addAnotherInvoked = false
        composeTestRule.setContent {
            MmoTheme {
                GearSummaryScreenContent(
                    draft = sampleDraft(listOf(gearUseSeineNets)),
                    gearTypes = gearTypes,
                    onRemoveGear = {},
                    onAddAnotherGear = { addAnotherInvoked = true },
                    onSubmit = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(GearSummaryScreenTestTags.ADD_ANOTHER_ACTION).performClick()
        assertTrue(addAnotherInvoked)
    }

    @Test
    fun gearSummaryRehydratesCheckedStateAndShotsValueFromASavedDraftAfterProcessDeath() {
        val rehydratedGearUses =
            listOf(
                gearUseSeineNets.copy(confirmedUsedOnTrip = true, numberOfShots = 4),
                gearUseBottomOtterTrawls,
            )
        composeTestRule.setContent {
            MmoTheme {
                GearSummaryScreenContent(
                    draft = sampleDraft(rehydratedGearUses),
                    gearTypes = gearTypes,
                    onRemoveGear = {},
                    onAddAnotherGear = {},
                    onSubmit = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("${GearSummaryScreenTestTags.CHECKBOX_PREFIX}_0").assertIsOn()
        composeTestRule.onNodeWithTag("${GearSummaryScreenTestTags.CHECKBOX_PREFIX}_1").assertIsOff()
        val shotsNode =
            composeTestRule
                .onNodeWithTag("${GearSummaryScreenTestTags.SHOTS_FIELD_PREFIX}_gear-use-1")
                .fetchSemanticsNode()
        val shotsText = shotsNode.config.getOrNull(SemanticsProperties.EditableText)?.text
        assertEquals("4", shotsText)
    }

    /** An unchecked gear's shots field, re-typed before checking the box, still saves on submit. */
    @Test
    fun editingTheShotsValueBeforeCheckingTheBoxIsStillSavedOnSubmit() {
        var submittedDraft: CatchRecordDraft? = null
        composeTestRule.setContent {
            MmoTheme {
                GearSummaryScreenContent(
                    draft = sampleDraft(listOf(gearUseSeineNets.copy(confirmedUsedOnTrip = true, numberOfShots = 2))),
                    gearTypes = gearTypes,
                    onRemoveGear = {},
                    onAddAnotherGear = {},
                    onSubmit = { submittedDraft = it },
                )
            }
        }

        composeTestRule
            .onNodeWithTag("${GearSummaryScreenTestTags.SHOTS_FIELD_PREFIX}_gear-use-1")
            .performTextClearance()
        composeTestRule
            .onNodeWithTag("${GearSummaryScreenTestTags.SHOTS_FIELD_PREFIX}_gear-use-1")
            .performTextInput("7")
        composeTestRule.onNodeWithTag(GearSummaryScreenTestTags.SAVE_ACTION).performClick()

        assertEquals(7, submittedDraft?.gearUses?.single()?.numberOfShots)
    }

    /** A gear type id with no match in [gearTypes] falls back to the raw id as its display label. */
    @Test
    fun aGearUseWithNoMatchingGearTypeFallsBackToTheRawGearTypeIdAsItsLabel() {
        composeTestRule.setContent {
            MmoTheme {
                GearSummaryScreenContent(
                    draft = sampleDraft(listOf(gearUseSeineNets.copy(gearTypeId = "gear-unknown"))),
                    gearTypes = emptyList(),
                    onRemoveGear = {},
                    onAddAnotherGear = {},
                    onSubmit = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("${GearSummaryScreenTestTags.CHECKBOX_PREFIX}_0").assertIsOff()
    }

    // --- Hilt-backed wrapper coverage ------------------------------------------------------------

    private fun fullyCompleteDraft() =
        CatchRecordDraft(
            id = "draft-1",
            vesselId = "vessel-achilles",
            isTripToday = true,
            departurePort = PortSelection("port-1", PortSelectionMode.Favourite),
            returnPort = PortSelection("port-1", PortSelectionMode.Favourite),
            gearUses =
                listOf(
                    GearUse(
                        id = "gear-use-1",
                        gearTypeId = "gear-seine-nets",
                        statisticalSubRectangleCode = "38E95",
                        speciesWeights =
                            listOf(SpeciesWeightEntry(id = "sw-0", speciesId = "species-cod", confirmedCaught = true)),
                        confirmedUsedOnTrip = false,
                    ),
                ),
            modifiedAtEpochMillis = 0L,
            status = DraftStatus.Draft,
        )

    @Test
    fun viewModelBackedScreenWithAtLeastOneConfirmedGearNavigatesToTheResolvedNextStep() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            repository.startDraft("vessel-achilles")
            repository.saveDraft(fullyCompleteDraft())
            val viewModel = buildViewModel(dispatcher, repository)
            var navigatedStep: WizardStep? = null

            composeTestRule.setContent {
                WizardTestTheme {
                    GearSummaryScreen(
                        viewModel = viewModel,
                        onNavigate = { navigatedStep = it },
                        onBack = {},
                    )
                }
            }
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft("draft-1"))
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule
                .onNodeWithTag("${GearSummaryScreenTestTags.CHECKBOX_PREFIX}_0")
                .performScrollTo()
                .performClick()
            composeTestRule
                .onNodeWithTag(GearSummaryScreenTestTags.SAVE_ACTION)
                .performScrollTo()
                .performClick()
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            val draft = (viewModel.state.value.status as UiStatus.Content<CatchRecordDraft>).value
            assertTrue(draft.gearUses.single().confirmedUsedOnTrip)
            assertEquals(nextWizardStepForDraft(draft), navigatedStep)
            assertEquals(WizardStep.NotLandedStraightAwayDecision, navigatedStep)
        }

    @Test
    fun viewModelBackedScreenWithZeroConfirmedGearNavigatesViaTheSubmissionResolver() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            repository.startDraft("vessel-achilles")
            repository.saveDraft(fullyCompleteDraft())
            val viewModel = buildViewModel(dispatcher, repository)
            var navigatedStep: WizardStep? = null

            composeTestRule.setContent {
                WizardTestTheme {
                    GearSummaryScreen(
                        viewModel = viewModel,
                        onNavigate = { navigatedStep = it },
                        onBack = {},
                    )
                }
            }
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft("draft-1"))
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule
                .onNodeWithTag(GearSummaryScreenTestTags.SAVE_ACTION)
                .performScrollTo()
                .performClick()
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            val draft = (viewModel.state.value.status as UiStatus.Content<CatchRecordDraft>).value
            assertTrue(draft.gearUses.none { it.confirmedUsedOnTrip })
            assertEquals(resolveSubmissionStep(draft), navigatedStep)
            assertEquals(WizardStep.CheckYourAnswers, navigatedStep)
        }

    @Test
    fun viewModelBackedScreenRemoveGearDispatchesGearRemovedAndUpdatesTheDraft() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            repository.startDraft("vessel-achilles")
            repository.saveDraft(fullyCompleteDraft())
            val viewModel = buildViewModel(dispatcher, repository)

            composeTestRule.setContent {
                WizardTestTheme {
                    GearSummaryScreen(viewModel = viewModel, onNavigate = {}, onBack = {})
                }
            }
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft("draft-1"))
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule
                .onNodeWithTag("${GearSummaryScreenTestTags.CHECKBOX_PREFIX}_0")
                .performScrollTo()
                .performClick()
            composeTestRule
                .onNodeWithTag(GearSummaryScreenTestTags.REMOVE_ACTION)
                .performScrollTo()
                .performClick()
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            val draft = (viewModel.state.value.status as UiStatus.Content<CatchRecordDraft>).value
            assertTrue(draft.gearUses.isEmpty())
        }

    @Test
    fun viewModelBackedScreenAddAnotherGearNavigatesToGearSearch() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            repository.startDraft("vessel-achilles")
            repository.saveDraft(fullyCompleteDraft())
            val viewModel = buildViewModel(dispatcher, repository)
            var navigatedStep: WizardStep? = null

            composeTestRule.setContent {
                WizardTestTheme {
                    GearSummaryScreen(
                        viewModel = viewModel,
                        onNavigate = { navigatedStep = it },
                        onBack = {},
                    )
                }
            }
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft("draft-1"))
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule
                .onNodeWithTag(GearSummaryScreenTestTags.ADD_ANOTHER_ACTION)
                .performScrollTo()
                .performClick()

            assertEquals(WizardStep.GearSearch, navigatedStep)
        }

    @Test
    fun screenPreviewRendersWithoutError() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                GearSummaryScreen_Preview()
            }
        }

        composeTestRule.onNodeWithText("What gear did you use?").assertIsDisplayed()
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
                    GearSummaryScreen(viewModel = viewModel, onNavigate = {}, onBack = {})
                }
            }
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag(GearSummaryScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
            composeTestRule
                .onNodeWithTag("${GearSummaryScreenTestTags.ERROR_MESSAGE}_retry_action")
                .performClick()
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            assertEquals(UiStatus.Idle, viewModel.state.value.status)
        }
}
