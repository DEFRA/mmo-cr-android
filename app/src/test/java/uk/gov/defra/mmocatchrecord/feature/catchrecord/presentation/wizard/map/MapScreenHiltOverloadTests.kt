@file:Suppress("detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.map

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelection
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelectionMode
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldKeys
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowEvent
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewModel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeCatchRecordSubmissionRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeCatchRecordSyncScheduler
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeNetworkConnectivityChecker
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeReferenceDataRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardStep
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardTestTheme
import uk.gov.defra.mmocatchrecord.mapdata.MapDataset
import uk.gov.defra.mmocatchrecord.mapdata.SerializableBBox
import uk.gov.defra.mmocatchrecord.mapdata.SerializableMultiPolygon
import uk.gov.defra.mmocatchrecord.mapdata.SerializablePoint
import uk.gov.defra.mmocatchrecord.mapdata.SerializablePolygon
import uk.gov.defra.mmocatchrecord.mapdata.SerializableRing
import uk.gov.defra.mmocatchrecord.mapdata.SerializableSubRectangle

/**
 * Covers [MapScreen]'s **Hilt-backed** overload — the one real navigation (`MmoNavHost`) actually calls,
 * which resolves its own [MapDataViewModel] via `hiltViewModel()`. [MapScreenTests] already exhaustively
 * covers the pure, state-based `internal` overload those Hilt-free tests call directly, but never exercises
 * this outer wrapper's own body (the `viewModel.state` collection, the add-vs-edit `onSubmit` branching that
 * dispatches [CatchRecordFlowEvent.SaveAndContinue]/[CatchRecordFlowEvent.EditGearStatRectangle], or the
 * [CatchRecordFlowEvent.Retry] wiring).
 *
 * [LocalMapDataUiState] lets this test supply a fixed [MapDataUiState] without a real Hilt component —
 * mirrors
 * [uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.LocalWizardScaffoldState]'s
 * identical "resolve from Hilt in the running app, from a `CompositionLocal` in a test" seam, already
 * established for exactly this problem (`AppLanguageViewModel`/`ConnectivityViewModel` inside
 * `CatchRecordWizardScaffold`, which this screen also renders). The `viewModel` parameter itself is a real
 * [CatchRecordFlowViewModel] backed by fakes (not Hilt) — the same pattern `CheckYourAnswersNavigationTests`
 * already uses for the equally Hilt-free-but-ViewModel-driven submission screens.
 */
@RunWith(RobolectricTestRunner::class)
class MapScreenHiltOverloadTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    /** Seeded, in-memory [CatchRecordDraftRepository] — mirrors `CheckYourAnswersNavigationTests`'. */
    private class FakeDraftRepository(
        seed: CatchRecordDraft,
    ) : CatchRecordDraftRepository {
        private val drafts = mutableMapOf(seed.id to seed)

        /** When `true`, the next [getAnyActiveDraft] call fails once (then resets) — drives the Retry test. */
        var failNextGetAnyActiveDraft: Boolean = false

        override suspend fun getActiveDraft(vesselId: String): Result<CatchRecordDraft?> =
            Result.success(drafts.values.firstOrNull { it.vesselId == vesselId && it.status.isActive })

        override suspend fun getAnyActiveDraft(): Result<CatchRecordDraft?> {
            if (failNextGetAnyActiveDraft) {
                failNextGetAnyActiveDraft = false
                return Result.failure(java.io.IOException("Simulated failure"))
            }
            return Result.success(drafts.values.firstOrNull { it.status.isActive })
        }

        override suspend fun getDraftById(draftId: String): Result<CatchRecordDraft?> = Result.success(drafts[draftId])

        override suspend fun startDraft(candidate: CatchRecordDraft): Result<CatchRecordDraft> =
            Result.failure(UnsupportedOperationException("Not exercised by this test"))

        override suspend fun saveDraft(draft: CatchRecordDraft): Result<CatchRecordDraft> {
            drafts[draft.id] = draft
            return Result.success(draft)
        }

        override suspend fun deleteDraft(draftId: String): Result<Unit> {
            drafts.remove(draftId)
            return Result.success(Unit)
        }

        override suspend fun markReadyToSubmit(draftId: String): Result<CatchRecordDraft> =
            Result.failure(UnsupportedOperationException("Not exercised by this test"))

        fun draftOrNull(draftId: String): CatchRecordDraft? = drafts[draftId]
    }

    private fun pendingGearUse() =
        GearUse(
            id = "gear-use-1",
            gearTypeId = "gear-seine-nets",
            statisticalSubRectangleCode = null,
            measurements = mapOf(GearMeasurementFieldKeys.MESH_SIZE_MM to MeasurementValue.Numeric(100.0, "mm")),
            confirmedUsedOnTrip = true,
        )

    private fun draftWith(gearUse: GearUse): CatchRecordDraft =
        CatchRecordDraft(
            id = "draft-1",
            vesselId = "vessel-achilles",
            departurePort = PortSelection("port-hastings", PortSelectionMode.FirstTime),
            gearUses = listOf(gearUse),
            modifiedAtEpochMillis = 0L,
            status = DraftStatus.Draft,
        )

    private fun buildViewModel(draftRepository: CatchRecordDraftRepository): CatchRecordFlowViewModel =
        CatchRecordFlowViewModel(
            draftRepository,
            FakeReferenceDataRepository(),
            FakeCatchRecordSubmissionRepository(),
            FakeNetworkConnectivityChecker(),
            FakeCatchRecordSyncScheduler(),
            { 0L },
            { "generated-id" },
            Dispatchers.Default,
        )

    /** A minimal, sea-overlapping-everywhere [MapDataset] — any tap on the rendered Canvas hits it. */
    private fun mapDatasetFixture(): MapDataset {
        val ring =
            SerializableRing(
                listOf(
                    SerializablePoint(-20.0, 30.0),
                    SerializablePoint(20.0, 30.0),
                    SerializablePoint(20.0, 75.0),
                    SerializablePoint(-20.0, 75.0),
                    SerializablePoint(-20.0, 30.0),
                ),
            )
        val subRectangle =
            SerializableSubRectangle(
                code = "38E95",
                icesName = "Test area",
                areaKm2 = 1.0,
                bbox = SerializableBBox(minLon = -20.0, minLat = 30.0, maxLon = 20.0, maxLat = 75.0),
                centroid = SerializablePoint(0.0, 55.0),
                geometry = SerializableMultiPolygon(listOf(SerializablePolygon(ring, emptyList()))),
                isSeaOverlapping = true,
            )
        return MapDataset(
            formatVersion = MapDataset.CURRENT_FORMAT_VERSION,
            land = emptyList(),
            subRectangles = listOf(subRectangle),
            ports = emptyList(),
        )
    }

    private fun launchMapScreen(
        viewModel: CatchRecordFlowViewModel,
        editGearUseId: String? = null,
        onNavigate: (WizardStep) -> Unit = {},
        onBack: () -> Unit = {},
        onRetryMapData: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalMapDataUiState provides
                    MapDataUiState(UiStatus.Content(mapDatasetFixture()), onRetryMapData = onRetryMapData),
            ) {
                WizardTestTheme {
                    MapScreen(
                        viewModel = viewModel,
                        onNavigate = onNavigate,
                        onBack = onBack,
                        editGearUseId = editGearUseId,
                    )
                }
            }
        }
        viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
        composeTestRule.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.status is UiStatus.Content<*> }
        composeTestRule.waitForIdle()
    }

    @Test
    fun addPathTappingACellAndSavingDispatchesSaveAndContinueAndNavigatesToTheResolvedNextStep() {
        val draftRepository = FakeDraftRepository(draftWith(pendingGearUse()))
        val viewModel = buildViewModel(draftRepository)
        var navigatedStep: WizardStep? = null

        launchMapScreen(viewModel, onNavigate = { navigatedStep = it })

        composeTestRule
            .onNodeWithText("Where was the majority of your catch caught using seine nets (mesh size 100mm)?")
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MapScreenTestTags.MAP).performTouchInput { click() }
        // Robolectric only runs Compose's Canvas draw phase on captureToImage() — see MapCanvasTests.
        composeTestRule.onNodeWithTag(MapScreenTestTags.MAP).captureToImage()
        composeTestRule.onNodeWithTag(MapScreenTestTags.GRID_SAVE_ACTION).performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            draftRepository
                .draftOrNull("draft-1")
                ?.gearUses
                ?.single()
                ?.statisticalSubRectangleCode != null
        }
        assertEquals(
            "38E95",
            draftRepository
                .draftOrNull("draft-1")
                ?.gearUses
                ?.single()
                ?.statisticalSubRectangleCode,
        )
        assertTrue("onNavigate must be invoked with the draft's resolved next wizard step", navigatedStep != null)
    }

    @Test
    fun editPathTappingACellAndSavingDispatchesEditGearStatRectangleAndNavigatesToCheckYourAnswers() {
        val draftRepository = FakeDraftRepository(draftWith(pendingGearUse()))
        val viewModel = buildViewModel(draftRepository)
        var navigatedStep: WizardStep? = null

        launchMapScreen(viewModel, editGearUseId = "gear-use-1", onNavigate = { navigatedStep = it })

        composeTestRule.onNodeWithTag(MapScreenTestTags.MAP).performTouchInput { click() }
        composeTestRule.onNodeWithTag(MapScreenTestTags.MAP).captureToImage()
        composeTestRule.onNodeWithTag(MapScreenTestTags.GRID_SAVE_ACTION).performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) { navigatedStep == WizardStep.CheckYourAnswers }
        assertEquals(WizardStep.CheckYourAnswers, navigatedStep)
        // onNavigate fires synchronously on click, but EditGearStatRectangle persists via
        // viewModelScope asynchronously (see persistGearEdit) — wait for it rather than racing it,
        // mirroring the add-path test above.
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            draftRepository
                .draftOrNull("draft-1")
                ?.gearUses
                ?.single()
                ?.statisticalSubRectangleCode != null
        }
        assertEquals(
            "38E95",
            draftRepository
                .draftOrNull("draft-1")
                ?.gearUses
                ?.single()
                ?.statisticalSubRectangleCode,
        )
    }

    @Test
    fun backActionOnTheRealHiltBackedOverloadInvokesTheSuppliedOnBackCallback() {
        val draftRepository = FakeDraftRepository(draftWith(pendingGearUse()))
        val viewModel = buildViewModel(draftRepository)
        var backInvoked = false

        launchMapScreen(viewModel, onBack = { backInvoked = true })

        composeTestRule.onNodeWithText("Back").performClick()

        assertTrue("onBack must be invoked when the scaffold's back action is tapped", backInvoked)
    }

    @Test
    fun retryActionOnAnOuterLoadErrorRedispatchesEnterFlowAndRecoversToContent() {
        val draftRepository = FakeDraftRepository(draftWith(pendingGearUse()))
        draftRepository.failNextGetAnyActiveDraft = true
        val viewModel = buildViewModel(draftRepository)

        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalMapDataUiState provides
                    MapDataUiState(UiStatus.Content(mapDatasetFixture()), onRetryMapData = {}),
            ) {
                WizardTestTheme {
                    MapScreen(viewModel = viewModel, onNavigate = {}, onBack = {})
                }
            }
        }

        // Force the outer wizard state into a retryable error (a simulated repository I/O failure), then
        // recover via the real ViewModel's Retry event — exercising the Hilt-backed overload's `onRetry`
        // wiring (`{ viewModel.dispatch(CatchRecordFlowEvent.Retry) }`) end-to-end.
        viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
        composeTestRule.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.status is UiStatus.Error }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(MapScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("${MapScreenTestTags.ERROR_MESSAGE}_retry_action")
            .performScrollTo()
            .performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.status is UiStatus.Content<*> }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MapScreenTestTags.MAP).assertIsDisplayed()
    }
}
