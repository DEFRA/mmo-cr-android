package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.landing

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.common.design.INVALID_INPUT_MESSAGE_TAG_SUFFIX
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.NotLandedSpeciesEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelection
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelectionMode
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SpeciesWeightEntry
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

/** JVM coverage for Phase 5B content and Hilt-backed wrapper paths. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NotLandedStraightAwayScreensTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val cod =
        Species(
            id = "species-cod",
            name = "Atlantic cod (COD)",
            faoCode = "COD",
            weightPrecision = SpeciesWeightPrecision.OneDecimalPlace,
        )
    private val haddock =
        Species(
            id = "species-haddock",
            name = "Haddock (HAD)",
            faoCode = "HAD",
            weightPrecision = SpeciesWeightPrecision.WholeNumber,
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
    ) = CatchRecordFlowViewModel(
        repository,
        FakeReferenceDataRepository(),
        FakeCatchRecordSubmissionRepository(),
        FakeNetworkConnectivityChecker(),
        FakeCatchRecordSyncScheduler(),
        { 0L },
        { "gear-use-1" },
        dispatcher,
    )

    // --- Screen 3: Yes/No decision -------------------------------------------------------------

    @Test
    fun selectingYesSubmitsTrue() {
        var submitted: Boolean? = null
        composeTestRule.setContent {
            MmoTheme {
                NotLandedStraightAwayDecisionScreenContent(initialValue = null, onSubmit = { submitted = it })
            }
        }

        composeTestRule.onNodeWithTag("${NotLandedStraightAwayDecisionScreenTestTags.OPTION_PREFIX}_0").performClick()
        composeTestRule.onNodeWithTag(NotLandedStraightAwayDecisionScreenTestTags.SAVE_ACTION).performClick()

        assertEquals(true, submitted)
    }

    @Test
    fun selectingNoSubmitsFalse() {
        var submitted: Boolean? = null
        composeTestRule.setContent {
            MmoTheme {
                NotLandedStraightAwayDecisionScreenContent(initialValue = null, onSubmit = { submitted = it })
            }
        }

        composeTestRule.onNodeWithTag("${NotLandedStraightAwayDecisionScreenTestTags.OPTION_PREFIX}_1").performClick()
        composeTestRule.onNodeWithTag(NotLandedStraightAwayDecisionScreenTestTags.SAVE_ACTION).performClick()

        assertEquals(false, submitted)
    }

    @Test
    fun noSelectionOnSaveShowsRequiredErrorAndDoesNotSubmit() {
        var submitted: Boolean? = null
        composeTestRule.setContent {
            MmoTheme {
                NotLandedStraightAwayDecisionScreenContent(initialValue = null, onSubmit = { submitted = it })
            }
        }

        composeTestRule.onNodeWithTag(NotLandedStraightAwayDecisionScreenTestTags.SAVE_ACTION).performClick()

        composeTestRule.onNodeWithTag(NotLandedStraightAwayDecisionScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
        assertNull(submitted)
    }

    // --- Screen 4: species checklist ------------------------------------------------------------

    private fun draftWithConfirmedSpecies(speciesIds: List<String>) =
        CatchRecordDraft(
            id = "draft-1",
            vesselId = "vessel-1",
            gearUses =
                listOf(
                    GearUse(
                        id = "gear-use-1",
                        gearTypeId = "gear-seine-nets",
                        statisticalSubRectangleCode = "38E95",
                        speciesWeights =
                            speciesIds.mapIndexed { index, id ->
                                SpeciesWeightEntry(id = "sw-$index", speciesId = id, confirmedCaught = true)
                            },
                        confirmedUsedOnTrip = true,
                    ),
                ),
            modifiedAtEpochMillis = 0L,
            status = DraftStatus.Draft,
        )

    @Test
    fun checklistShowsEveryDistinctConfirmedSpeciesFromTheDraft() {
        composeTestRule.setContent {
            MmoTheme {
                NotLandedStraightAwaySpeciesScreenContent(
                    draft = draftWithConfirmedSpecies(listOf(cod.id, haddock.id)),
                    speciesList = listOf(cod, haddock),
                    onSubmit = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Atlantic cod (COD)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Haddock (HAD)").assertIsDisplayed()
    }

    @Test
    fun checkingASpeciesRevealsTheKeptOnboardFieldAndSubmittingSavesIt() {
        var submitted: CatchRecordDraft? = null
        composeTestRule.setContent {
            MmoTheme {
                NotLandedStraightAwaySpeciesScreenContent(
                    draft = draftWithConfirmedSpecies(listOf(cod.id)),
                    speciesList = listOf(cod),
                    onSubmit = { submitted = it },
                )
            }
        }

        composeTestRule.onNodeWithTag("${NotLandedStraightAwaySpeciesScreenTestTags.CHECKBOX_PREFIX}_0").performClick()
        composeTestRule
            .onNodeWithTag("${NotLandedStraightAwaySpeciesScreenTestTags.WEIGHT_FIELD_PREFIX}_${cod.id}")
            .performTextInput("3.5")
        composeTestRule.onNodeWithTag(NotLandedStraightAwaySpeciesScreenTestTags.SAVE_ACTION).performClick()

        val entry = submitted?.notLandedSpeciesEntries?.single()
        assertEquals(cod.id, entry?.speciesId)
        assertEquals(3.5, entry?.weightAboveMinimumSizeKeptOnboardKg)
    }

    @Test
    fun keptOnboardFieldBlocksASecondDecimalDigitOrPointAndAnnouncesWhy() {
        composeTestRule.setContent {
            MmoTheme {
                NotLandedStraightAwaySpeciesScreenContent(
                    draft = draftWithConfirmedSpecies(listOf(cod.id)),
                    speciesList = listOf(cod),
                    onSubmit = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("${NotLandedStraightAwaySpeciesScreenTestTags.CHECKBOX_PREFIX}_0").performClick()
        val tag = "${NotLandedStraightAwaySpeciesScreenTestTags.WEIGHT_FIELD_PREFIX}_${cod.id}"
        val hintTag = "${tag}_$INVALID_INPUT_MESSAGE_TAG_SUFFIX"
        val field = composeTestRule.onNodeWithTag(tag)
        field.performTextInput("5.5")
        composeTestRule.onNodeWithTag(hintTag).assertDoesNotExist()

        field.performTextInput("5")
        field.assertTextEquals("5.5")
        composeTestRule
            .onNodeWithTag(hintTag)
            .assertTextEquals("Weight must be a number with up to 1 decimal place, like 12.5")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))

        field.performTextInput(".")
        field.assertTextEquals("5.5")

        field.performTextClearance()
        composeTestRule.onNodeWithTag(hintTag).assertDoesNotExist()
        field.performTextInput("1.2.3")
        field.assertTextEquals("")
        composeTestRule.onNodeWithTag(hintTag).assertIsDisplayed()
    }

    @Test
    fun keptOnboardFieldForAWholeNumberSpeciesExplainsWhyADecimalPointIsBlocked() {
        composeTestRule.setContent {
            MmoTheme {
                NotLandedStraightAwaySpeciesScreenContent(
                    draft = draftWithConfirmedSpecies(listOf(haddock.id)),
                    speciesList = listOf(haddock),
                    onSubmit = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("${NotLandedStraightAwaySpeciesScreenTestTags.CHECKBOX_PREFIX}_0").performClick()
        val tag = "${NotLandedStraightAwaySpeciesScreenTestTags.WEIGHT_FIELD_PREFIX}_${haddock.id}"
        val field = composeTestRule.onNodeWithTag(tag)
        field.performTextInput("12")
        field.performTextInput(".")
        field.assertTextEquals("12")
        composeTestRule
            .onNodeWithTag("${tag}_$INVALID_INPUT_MESSAGE_TAG_SUFFIX")
            .assertTextEquals("Weight must be a whole number, like 12")
    }

    @Test
    fun noSpeciesCheckedOnSaveShowsChecklistRequiredError() {
        var submitted: CatchRecordDraft? = null
        composeTestRule.setContent {
            MmoTheme {
                NotLandedStraightAwaySpeciesScreenContent(
                    draft = draftWithConfirmedSpecies(listOf(cod.id)),
                    speciesList = listOf(cod),
                    onSubmit = { submitted = it },
                )
            }
        }

        composeTestRule.onNodeWithTag(NotLandedStraightAwaySpeciesScreenTestTags.SAVE_ACTION).performClick()

        composeTestRule.onNodeWithTag(NotLandedStraightAwaySpeciesScreenTestTags.CHECKLIST_ERROR).assertIsDisplayed()
        assertNull(submitted)
    }

    @Test
    fun blankKeptOnboardFieldForACheckedSpeciesShowsRequiredErrorInSummary() {
        composeTestRule.setContent {
            MmoTheme {
                NotLandedStraightAwaySpeciesScreenContent(
                    draft = draftWithConfirmedSpecies(listOf(cod.id)),
                    speciesList = listOf(cod),
                    onSubmit = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("${NotLandedStraightAwaySpeciesScreenTestTags.CHECKBOX_PREFIX}_0").performClick()
        composeTestRule.onNodeWithTag(NotLandedStraightAwaySpeciesScreenTestTags.SAVE_ACTION).performClick()

        composeTestRule.onNodeWithTag(NotLandedStraightAwaySpeciesScreenTestTags.ERROR_SUMMARY).assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText("Enter the weight above minimum size kept onboard or in keep pots in kilograms")
            .onFirst()
            .assertIsDisplayed()
    }

    /** Stale species ids are silently dropped rather than crashing or blocking save. */
    @Test
    fun aCheckedSpeciesMissingFromTheSuppliedSpeciesListIsDroppedFromTheSubmittedEntries() {
        var submitted: CatchRecordDraft? = null
        composeTestRule.setContent {
            MmoTheme {
                NotLandedStraightAwaySpeciesScreenContent(
                    draft = draftWithConfirmedSpecies(listOf("species-unknown")),
                    speciesList = emptyList(),
                    onSubmit = { submitted = it },
                )
            }
        }

        composeTestRule.onNodeWithTag("${NotLandedStraightAwaySpeciesScreenTestTags.CHECKBOX_PREFIX}_0").performClick()
        composeTestRule.onNodeWithTag(NotLandedStraightAwaySpeciesScreenTestTags.SAVE_ACTION).performClick()

        assertEquals(emptyList<NotLandedSpeciesEntry>(), submitted?.notLandedSpeciesEntries)
    }

    @Test
    fun zeroKeptOnboardFieldShowsBelowMinimumRangeError() {
        composeTestRule.setContent {
            MmoTheme {
                NotLandedStraightAwaySpeciesScreenContent(
                    draft = draftWithConfirmedSpecies(listOf(cod.id)),
                    speciesList = listOf(cod),
                    onSubmit = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("${NotLandedStraightAwaySpeciesScreenTestTags.CHECKBOX_PREFIX}_0").performClick()
        composeTestRule
            .onNodeWithTag("${NotLandedStraightAwaySpeciesScreenTestTags.WEIGHT_FIELD_PREFIX}_${cod.id}")
            .performTextInput("0")
        composeTestRule.onNodeWithTag(NotLandedStraightAwaySpeciesScreenTestTags.SAVE_ACTION).performClick()

        composeTestRule
            .onAllNodesWithText("Weight for Atlantic cod must be more than 0kg")
            .onFirst()
            .assertIsDisplayed()
    }

    @Test
    fun keptOnboardFieldOverTheMaximumShowsAboveMaximumRangeError() {
        composeTestRule.setContent {
            MmoTheme {
                NotLandedStraightAwaySpeciesScreenContent(
                    draft = draftWithConfirmedSpecies(listOf(cod.id)),
                    speciesList = listOf(cod),
                    onSubmit = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("${NotLandedStraightAwaySpeciesScreenTestTags.CHECKBOX_PREFIX}_0").performClick()
        composeTestRule
            .onNodeWithTag("${NotLandedStraightAwaySpeciesScreenTestTags.WEIGHT_FIELD_PREFIX}_${cod.id}")
            .performTextInput("10000.1")
        composeTestRule.onNodeWithTag(NotLandedStraightAwaySpeciesScreenTestTags.SAVE_ACTION).performClick()

        composeTestRule
            .onAllNodesWithText("Weight for Atlantic cod must be 10,000kg or less")
            .onFirst()
            .assertIsDisplayed()
    }

    /** Decimal saved values can only reach WholeNumber validation from a reopened draft, not typed input. */
    @Test
    fun wholeNumberSpeciesRejectsADecimalWeightWithAPrecisionError() {
        val draft =
            draftWithConfirmedSpecies(listOf(haddock.id)).copy(
                notLandedSpeciesEntries =
                    listOf(
                        NotLandedSpeciesEntry(speciesId = haddock.id, weightAboveMinimumSizeKeptOnboardKg = 3.5),
                    ),
            )
        composeTestRule.setContent {
            MmoTheme {
                NotLandedStraightAwaySpeciesScreenContent(
                    draft = draft,
                    speciesList = listOf(haddock),
                    onSubmit = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(NotLandedStraightAwaySpeciesScreenTestTags.SAVE_ACTION).performClick()

        composeTestRule
            .onAllNodesWithText("Weight for Haddock must be a whole number")
            .onFirst()
            .assertIsDisplayed()
    }

    // --- Hilt-backed wrapper coverage ------------------------------------------------------------

    private fun fullyCompleteDraft(notLandedStraightAway: Boolean? = null) =
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
                            listOf(SpeciesWeightEntry(id = "sw-0", speciesId = cod.id, confirmedCaught = true)),
                        confirmedUsedOnTrip = true,
                    ),
                ),
            notLandedStraightAway = notLandedStraightAway,
            modifiedAtEpochMillis = 0L,
            status = DraftStatus.Draft,
        )

    @Test
    fun viewModelBackedDecisionScreenSubmitsAndNavigatesToTheResolvedNextStep() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            repository.startDraft("vessel-achilles")
            repository.saveDraft(fullyCompleteDraft())
            val viewModel = buildViewModel(dispatcher, repository)
            var navigatedStep: WizardStep? = null

            composeTestRule.setContent {
                WizardTestTheme {
                    NotLandedStraightAwayDecisionScreen(
                        viewModel = viewModel,
                        onNavigate = { navigatedStep = it },
                        onBack = {},
                    )
                }
            }
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule
                .onNodeWithTag("${NotLandedStraightAwayDecisionScreenTestTags.OPTION_PREFIX}_0")
                .performClick()
            composeTestRule.onNodeWithTag(NotLandedStraightAwayDecisionScreenTestTags.SAVE_ACTION).performClick()
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            val draft = (viewModel.state.value.status as UiStatus.Content<CatchRecordDraft>).value
            assertEquals(true, draft.notLandedStraightAway)
            assertEquals(nextWizardStepForDraft(draft), navigatedStep)
        }

    @Test
    fun anOuterLoadErrorOnTheDecisionScreenIsShownAsARetryableWizardErrorAndRecoversViaRetry() =
        runTest {
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            repository.failNextOperation = true
            val viewModel = buildViewModel(dispatcher = StandardTestDispatcher(testScheduler), repository = repository)

            composeTestRule.setContent {
                WizardTestTheme {
                    NotLandedStraightAwayDecisionScreen(viewModel = viewModel, onNavigate = {}, onBack = {})
                }
            }
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag(NotLandedStraightAwayDecisionScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
            composeTestRule
                .onNodeWithTag("${NotLandedStraightAwayDecisionScreenTestTags.ERROR_MESSAGE}_retry_action")
                .performClick()
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            assertEquals(UiStatus.Idle, viewModel.state.value.status)
        }

    @Test
    fun viewModelBackedSpeciesScreenSubmitsAndNavigatesToTheResolvedNextStep() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            repository.startDraft("vessel-achilles")
            repository.saveDraft(fullyCompleteDraft(notLandedStraightAway = true))
            val viewModel = buildViewModel(dispatcher, repository)
            var navigatedStep: WizardStep? = null

            composeTestRule.setContent {
                WizardTestTheme {
                    NotLandedStraightAwaySpeciesScreen(
                        viewModel = viewModel,
                        onNavigate = { navigatedStep = it },
                        onBack = {},
                    )
                }
            }
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule
                .onNodeWithTag("${NotLandedStraightAwaySpeciesScreenTestTags.CHECKBOX_PREFIX}_0")
                .performClick()
            composeTestRule
                .onNodeWithTag("${NotLandedStraightAwaySpeciesScreenTestTags.WEIGHT_FIELD_PREFIX}_${cod.id}")
                .performTextInput("3.5")
            composeTestRule.onNodeWithTag(NotLandedStraightAwaySpeciesScreenTestTags.SAVE_ACTION).performClick()
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            val draft = (viewModel.state.value.status as UiStatus.Content<CatchRecordDraft>).value
            assertEquals(1, draft.notLandedSpeciesEntries.size)
            assertEquals(nextWizardStepForDraft(draft), navigatedStep)
        }

    @Test
    fun anOuterLoadErrorOnTheSpeciesScreenIsShownAsARetryableWizardErrorAndRecoversViaRetry() =
        runTest {
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            repository.failNextOperation = true
            val viewModel = buildViewModel(dispatcher = StandardTestDispatcher(testScheduler), repository = repository)

            composeTestRule.setContent {
                WizardTestTheme {
                    NotLandedStraightAwaySpeciesScreen(viewModel = viewModel, onNavigate = {}, onBack = {})
                }
            }
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag(NotLandedStraightAwaySpeciesScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
            composeTestRule
                .onNodeWithTag("${NotLandedStraightAwaySpeciesScreenTestTags.ERROR_MESSAGE}_retry_action")
                .performClick()
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            assertEquals(UiStatus.Idle, viewModel.state.value.status)
        }
}
