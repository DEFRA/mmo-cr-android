package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.RecordStatusTag
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelection
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelectionMode
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.landing.NotLandedStraightAwayDecisionScreenContent
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.landing.NotLandedStraightAwayDecisionScreenTestTags
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.map.MapDataUiState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.map.MapScreen
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.map.MapScreenTestTags
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.submission.LateSubmissionWarningScreenContent
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.submission.LateSubmissionWarningScreenTestTags
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip.DeparturePortScreenTestTags
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip.PortSelectionStepContent
import uk.gov.defra.mmocatchrecord.feature.home.domain.CatchRecordSummary
import uk.gov.defra.mmocatchrecord.feature.home.domain.HomeSummary
import uk.gov.defra.mmocatchrecord.feature.home.presentation.CatchRecordsListSectionTestTags
import uk.gov.defra.mmocatchrecord.feature.home.presentation.HomeScreenContent
import uk.gov.defra.mmocatchrecord.feature.home.presentation.HomeScreenTestTags
import uk.gov.defra.mmocatchrecord.feature.home.presentation.HomeViewState
import uk.gov.defra.mmocatchrecord.mapdata.MapDataset
import uk.gov.defra.mmocatchrecord.mapdata.SerializableBBox
import uk.gov.defra.mmocatchrecord.mapdata.SerializableMultiPolygon
import uk.gov.defra.mmocatchrecord.mapdata.SerializablePoint
import uk.gov.defra.mmocatchrecord.mapdata.SerializablePolygon
import uk.gov.defra.mmocatchrecord.mapdata.SerializablePort
import uk.gov.defra.mmocatchrecord.mapdata.SerializableRing
import uk.gov.defra.mmocatchrecord.mapdata.SerializableSubRectangle

/** Business rules AC01, AC03, AC04, AC05, AC09 and AC12, each exercised with the device offline. */
class OfflineBusinessRulesTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun string(resId: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(resId)

    private fun assertOfflineBannerShown() {
        composeTestRule.onNodeWithTag(OFFLINE_BANNER).assertIsDisplayed()
    }

    @Composable
    private fun OfflineStep(content: @Composable () -> Unit) {
        WizardTestTheme(isOffline = true) {
            CatchRecordWizardScaffold(screenTestTag = "offline_step", title = "Offline step", onBack = {}) {
                content()
            }
        }
    }

    // --- Map fixtures (bundled offline dataset, no network) ------------------------------------------

    private val gearType = GearType(id = "gear-otter", name = "Otter trawls")
    private val hastings = Port("port-hastings", "Hastings", "AREA-HASTINGS")
    private val dover = Port("port-dover", "Dover", "AREA-DOVER")

    private fun area(
        code: String,
        lon: Double,
        lat: Double,
        isSea: Boolean = true,
    ): SerializableSubRectangle {
        val ring =
            SerializableRing(
                listOf(
                    SerializablePoint(lon - 0.1, lat - 0.1),
                    SerializablePoint(lon + 0.1, lat - 0.1),
                    SerializablePoint(lon + 0.1, lat + 0.1),
                    SerializablePoint(lon - 0.1, lat + 0.1),
                    SerializablePoint(lon - 0.1, lat - 0.1),
                ),
            )
        return SerializableSubRectangle(
            code = code,
            icesName = code.take(4),
            areaKm2 = 1.0,
            bbox = SerializableBBox(lon - 0.1, lat - 0.1, lon + 0.1, lat + 0.1),
            centroid = SerializablePoint(lon, lat),
            geometry = SerializableMultiPolygon(listOf(SerializablePolygon(ring, emptyList()))),
            isSeaOverlapping = isSea,
        )
    }

    private val seaAreas =
        listOf(
            area("38E95", lon = 0.75, lat = 54.75),
            area("38E98", lon = 0.25, lat = 54.60),
            area("38F02", lon = 1.10, lat = 54.85),
            area("37E85", lon = -1.50, lat = 54.30),
        )

    private val dataset =
        MapDataset(
            formatVersion = MapDataset.CURRENT_FORMAT_VERSION,
            land = emptyList(),
            subRectangles = seaAreas + area("40E01", lon = -2.0, lat = 55.5, isSea = false),
            ports = listOf(SerializablePort("GBHAS", "Hastings", SerializablePoint(0.6, 50.85))),
        )

    private fun setOfflineMapScreen(
        mapStatus: UiStatus<MapDataset> = UiStatus.Content(dataset),
        onSubmit: (CatchRecordDraft) -> Unit = {},
    ) {
        val draft =
            CatchRecordDraft(
                id = "draft-1",
                vesselId = "vessel-1",
                departurePort = PortSelection(hastings.id, PortSelectionMode.FirstTime),
                gearUses =
                    listOf(
                        GearUse(
                            id = "gear-use-1",
                            gearTypeId = gearType.id,
                            statisticalSubRectangleCode = null,
                            confirmedUsedOnTrip = true,
                        ),
                    ),
                modifiedAtEpochMillis = 0L,
                status = DraftStatus.Draft,
            )
        composeTestRule.setContent {
            WizardTestTheme(isOffline = true) {
                MapScreen(
                    state =
                        CatchRecordFlowViewState(
                            status = UiStatus.Content(draft),
                            gearTypes = listOf(gearType),
                            ports = listOf(hastings),
                        ),
                    onSubmit = onSubmit,
                    onBack = {},
                    mapDataState = MapDataUiState(mapStatus, onRetryMapData = {}),
                )
            }
        }
    }

    private fun openSuggestedList() {
        composeTestRule.onNodeWithTag(MapScreenTestTags.GRID_OTHER_ACTION).performScrollTo().performClick()
    }

    private fun radio(index: Int) =
        composeTestRule.onNodeWithTag("${MapScreenTestTags.RADIO_OPTION_PREFIX}_$index").performScrollTo()

    // --- AC01: mandatory single-choice questions (offline) --------------------------------------------

    @Test
    fun ac01_draftActionWithNoSelectionShowsValidationOffline() {
        var resumed = false
        var deleted = false
        composeTestRule.setContent {
            OfflineStep {
                DraftResumeScreenContent(onResume = { resumed = true }, onDeleteConfirmed = { deleted = true })
            }
        }
        assertOfflineBannerShown()

        composeTestRule.onNodeWithTag(DraftResumeScreenTestTags.SAVE_ACTION).performScrollTo().performClick()

        composeTestRule
            .onNodeWithTag(DraftResumeScreenTestTags.ERROR_MESSAGE)
            .assertIsDisplayed()
            .assertTextContains(string(R.string.wizard_select_action_error))
        assertFalse(resumed)
        assertFalse(deleted)
    }

    @Test
    fun ac01_draftActionContinuesOfflineOnceAnOptionIsSelected() {
        var resumed = false
        composeTestRule.setContent {
            OfflineStep { DraftResumeScreenContent(onResume = { resumed = true }, onDeleteConfirmed = {}) }
        }

        composeTestRule.onNodeWithTag("${DraftResumeScreenTestTags.OPTION_PREFIX}_0").performClick()
        composeTestRule.onNodeWithTag(DraftResumeScreenTestTags.SAVE_ACTION).performScrollTo().performClick()

        assertTrue(resumed)
        assertOfflineBannerShown()
    }

    @Test
    fun ac01_statisticalAreaWithNoSelectionShowsValidationOffline() {
        var submitted: CatchRecordDraft? = null
        setOfflineMapScreen(onSubmit = { submitted = it })
        assertOfflineBannerShown()
        openSuggestedList()

        composeTestRule.onNodeWithTag(MapScreenTestTags.RADIO_SAVE_ACTION).performScrollTo().performClick()

        composeTestRule
            .onNodeWithTag(MapScreenTestTags.ERROR_MESSAGE)
            .performScrollTo()
            .assertTextContains(string(R.string.gear_stat_rectangle_error_required))
        assertNull(submitted)
    }

    @Test
    fun ac01_retainedCatchWithNoSelectionShowsValidationOffline() {
        var submitted: Boolean? = null
        composeTestRule.setContent {
            OfflineStep {
                NotLandedStraightAwayDecisionScreenContent(initialValue = null, onSubmit = { submitted = it })
            }
        }
        assertOfflineBannerShown()

        composeTestRule
            .onNodeWithTag(NotLandedStraightAwayDecisionScreenTestTags.SAVE_ACTION)
            .performScrollTo()
            .performClick()

        composeTestRule
            .onNodeWithTag(NotLandedStraightAwayDecisionScreenTestTags.ERROR_MESSAGE)
            .assertIsDisplayed()
            .assertTextContains(string(R.string.not_landed_straight_away_decision_error))
        assertNull(submitted)
    }

    @Test
    fun ac01_retainedCatchContinuesOfflineOnceAnOptionIsSelected() {
        var submitted: Boolean? = null
        composeTestRule.setContent {
            OfflineStep {
                NotLandedStraightAwayDecisionScreenContent(initialValue = null, onSubmit = { submitted = it })
            }
        }

        composeTestRule.onNodeWithTag("${NotLandedStraightAwayDecisionScreenTestTags.OPTION_PREFIX}_1").performClick()
        composeTestRule
            .onNodeWithTag(NotLandedStraightAwayDecisionScreenTestTags.SAVE_ACTION)
            .performScrollTo()
            .performClick()

        assertEquals(false, submitted)
    }

    // --- AC03: suggested statistical areas nearest the departure port (offline) ------------------------

    @Test
    fun ac03_suggestedAreasAreListedNearestFirstFromBundledDataOffline() {
        setOfflineMapScreen()
        assertOfflineBannerShown()
        openSuggestedList()

        radio(0).assertTextContains("37E85")
        radio(1).assertTextContains("38E98")
        radio(2).assertTextContains("38E95")
        radio(3).assertTextContains("38F02")
        radio(seaAreas.size).assertTextContains(string(R.string.gear_stat_rectangle_other_option))
        composeTestRule.onNodeWithText("40E01").assertDoesNotExist()
    }

    @Test
    fun ac03_selectingASuggestedAreaSavesItOffline() {
        var submitted: CatchRecordDraft? = null
        setOfflineMapScreen(onSubmit = { submitted = it })
        openSuggestedList()

        radio(1).performClick()
        composeTestRule.onNodeWithTag(MapScreenTestTags.RADIO_SAVE_ACTION).performScrollTo().performClick()

        assertEquals("38E98", submitted?.gearUses?.single()?.statisticalSubRectangleCode)
    }

    // --- AC04: "Other" search for a different valid area (offline) ------------------------------------

    @Test
    fun ac04_otherSearchFindsAndSavesAValidAreaOffline() {
        var submitted: CatchRecordDraft? = null
        setOfflineMapScreen(onSubmit = { submitted = it })
        openSuggestedList()
        radio(seaAreas.size).performClick()
        assertOfflineBannerShown()

        composeTestRule.onNodeWithTag(MapScreenTestTags.AUTOCOMPLETE_FIELD).performScrollTo().performTextInput("38F")
        composeTestRule.onNodeWithTag("${MapScreenTestTags.SUGGESTION_PREFIX}_0").performScrollTo().performClick()
        composeTestRule.onNodeWithTag(MapScreenTestTags.AUTOCOMPLETE_SAVE_ACTION).performScrollTo().performClick()

        val saved = submitted?.gearUses?.single()
        assertEquals("38F02", saved?.statisticalSubRectangleCode)
        assertEquals(54.85, saved?.statisticalSubRectangleLatitude)
        assertEquals(1.10, saved?.statisticalSubRectangleLongitude)
    }

    @Test
    fun ac04_otherSearchRejectsAnUnknownAreaOffline() {
        var submitted: CatchRecordDraft? = null
        setOfflineMapScreen(onSubmit = { submitted = it })
        openSuggestedList()
        radio(seaAreas.size).performClick()

        composeTestRule.onNodeWithTag(MapScreenTestTags.AUTOCOMPLETE_FIELD).performScrollTo().performTextInput("99Z99")
        composeTestRule.onNodeWithTag(MapScreenTestTags.AUTOCOMPLETE_SAVE_ACTION).performScrollTo().performClick()

        composeTestRule.onNodeWithTag(MapScreenTestTags.ERROR_SUMMARY).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.gear_stat_rectangle_error_not_found)).assertIsDisplayed()
        assertNull(submitted)
    }

    // --- AC05: favourite ports available offline ------------------------------------------------------

    private fun setOfflinePortStep(
        favourites: List<Port>,
        onSubmit: (PortSelection) -> Unit,
    ) {
        composeTestRule.setContent {
            OfflineStep {
                PortSelectionStepContent(
                    initialSelection = null,
                    allPorts = listOf(hastings, dover),
                    favouritePorts = favourites,
                    autocompletePlaceholder = "Enter port",
                    autocompleteFieldTag = DeparturePortScreenTestTags.AUTOCOMPLETE_FIELD,
                    liveRegionTag = DeparturePortScreenTestTags.LIVE_REGION,
                    suggestionPrefix = DeparturePortScreenTestTags.SUGGESTION_PREFIX,
                    noMatchesTag = DeparturePortScreenTestTags.NO_MATCHES,
                    optionPrefix = DeparturePortScreenTestTags.FAVOURITE_OPTION_PREFIX,
                    addPortTag = DeparturePortScreenTestTags.ADD_PORT_ACTION,
                    saveTag = DeparturePortScreenTestTags.SAVE_ACTION,
                    errorTag = DeparturePortScreenTestTags.ERROR_MESSAGE,
                    onSubmit = onSubmit,
                )
            }
        }
    }

    @Test
    fun ac05_savedFavouritePortsAreListedAndSelectableOffline() {
        var selection: PortSelection? = null
        setOfflinePortStep(favourites = listOf(hastings, dover)) { selection = it }
        assertOfflineBannerShown()

        composeTestRule
            .onNodeWithTag("${DeparturePortScreenTestTags.FAVOURITE_OPTION_PREFIX}_0")
            .assertTextContains("Hastings")
        composeTestRule.onNodeWithTag("${DeparturePortScreenTestTags.FAVOURITE_OPTION_PREFIX}_1").performClick()
        composeTestRule.onNodeWithTag(DeparturePortScreenTestTags.SAVE_ACTION).performClick()

        assertEquals(PortSelection("port-dover", PortSelectionMode.Favourite), selection)
    }

    @Test
    fun ac05_portSearchUsesLocalReferenceDataOffline() {
        var selection: PortSelection? = null
        setOfflinePortStep(favourites = emptyList()) { selection = it }

        composeTestRule.onNodeWithTag(DeparturePortScreenTestTags.AUTOCOMPLETE_FIELD).performTextInput("Do")
        composeTestRule.onNodeWithTag("${DeparturePortScreenTestTags.SUGGESTION_PREFIX}_0").performClick()
        composeTestRule.onNodeWithTag(DeparturePortScreenTestTags.SAVE_ACTION).performClick()

        assertEquals(PortSelection("port-dover", PortSelectionMode.FirstTime), selection)
        assertOfflineBannerShown()
    }

    // --- AC09: late-submission warning (offline) ------------------------------------------------------

    @Test
    fun ac09_lateSubmissionWarningIsShownOffline() {
        composeTestRule.setContent {
            OfflineStep { LateSubmissionWarningScreenContent(onCheckTripEndDate = {}, onSubmit = {}) }
        }

        assertOfflineBannerShown()
        composeTestRule
            .onNodeWithText("Catch records must be submitted within 24 hours of a trip ending.", substring = true)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(LateSubmissionWarningScreenTestTags.CHECK_TRIP_END_DATE_LINK)
            .assertIsDisplayed()
    }

    @Test
    fun ac09_userCanAcknowledgeWarningAndProceedOffline() {
        var proceeded = false
        composeTestRule.setContent {
            OfflineStep {
                LateSubmissionWarningScreenContent(onCheckTripEndDate = {}, onSubmit = { proceeded = true })
            }
        }

        composeTestRule.onNodeWithTag(LateSubmissionWarningScreenTestTags.SAVE_ACTION).performScrollTo().performClick()

        assertTrue(proceeded)
    }

    // --- AC12: record statuses displayed (offline) ----------------------------------------------------

    @Test
    fun ac12_eachRecordShowsItsStatusWhileOffline() {
        val records =
            listOf(
                CatchRecordSummary("draft-1", null, "ACHILLES", null, RecordStatusTag.Draft),
                CatchRecordSummary("ready-1", "REF-2", "ACHILLES", DmyDate(2, 1, 2026), RecordStatusTag.ReadyToSubmit),
                CatchRecordSummary("unsent-1", "REF-3", "ACHILLES", DmyDate(3, 1, 2026), RecordStatusTag.AwaitingSync),
                CatchRecordSummary("sub-1", "REF-4", "ACHILLES", DmyDate(4, 1, 2026), RecordStatusTag.Submitted),
            )
        composeTestRule.setContent {
            WizardTestTheme(isOffline = true) {
                HomeScreenContent(
                    state = HomeViewState(status = UiStatus.Content(HomeSummary("user-1", records))),
                    currentLanguage = "en",
                    onLanguageToggle = {},
                    isOffline = true,
                    selectedTab = 0,
                    onTabSelected = {},
                    onSignOut = {},
                    onCreateCatchRecord = {},
                    onResumeDraft = {},
                    onDismissSyncConfirmationMessage = {},
                )
            }
        }
        assertOfflineBannerShown()

        val expected =
            mapOf(
                "draft-1" to R.string.status_draft,
                "ready-1" to R.string.status_ready_to_submit,
                "unsent-1" to R.string.status_awaiting_sync,
                "sub-1" to R.string.status_submitted,
            )
        expected.forEach { (id, label) ->
            composeTestRule
                .onNodeWithTag(HomeScreenTestTags.TAB_LIST)
                .performScrollToNode(hasTestTag(CatchRecordsListSectionTestTags.row(id)))
            composeTestRule
                .onNode(
                    hasText(string(label)) and hasAnyAncestor(hasTestTag(CatchRecordsListSectionTestTags.row(id))),
                    useUnmergedTree = true,
                ).assertIsDisplayed()
        }
    }

    // --- Edge cases (offline) ---------------------------------------------------------------------------

    @Test
    fun edge_draftActionErrorClearsOnceAnOptionIsChosenOffline() {
        composeTestRule.setContent { OfflineStep { DraftResumeScreenContent(onResume = {}, onDeleteConfirmed = {}) } }
        composeTestRule.onNodeWithTag(DraftResumeScreenTestTags.SAVE_ACTION).performScrollTo().performClick()
        composeTestRule.onNodeWithTag(DraftResumeScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()

        composeTestRule.onNodeWithTag("${DraftResumeScreenTestTags.OPTION_PREFIX}_0").performClick()

        composeTestRule.onNodeWithTag(DraftResumeScreenTestTags.ERROR_MESSAGE).assertDoesNotExist()
    }

    @Test
    fun edge_cancellingDeleteOfflineKeepsTheDraft() {
        var deleted = false
        composeTestRule.setContent {
            OfflineStep { DraftResumeScreenContent(onResume = {}, onDeleteConfirmed = { deleted = true }) }
        }

        composeTestRule.onNodeWithTag("${DraftResumeScreenTestTags.OPTION_PREFIX}_1").performClick()
        composeTestRule.onNodeWithTag(DraftResumeScreenTestTags.SAVE_ACTION).performScrollTo().performClick()
        composeTestRule.onNodeWithTag(DraftResumeScreenTestTags.CONFIRM_DIALOG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(DraftResumeScreenTestTags.CANCEL_DELETE).performClick()

        composeTestRule.onNodeWithTag(DraftResumeScreenTestTags.CONFIRM_DIALOG).assertDoesNotExist()
        assertFalse(deleted)
    }

    @Test
    fun edge_mapSaveWithNoAreaSelectedShowsValidationOffline() {
        var submitted: CatchRecordDraft? = null
        setOfflineMapScreen(onSubmit = { submitted = it })

        composeTestRule.onNodeWithTag(MapScreenTestTags.GRID_SAVE_ACTION).performScrollTo().performClick()

        composeTestRule
            .onNodeWithTag(MapScreenTestTags.ERROR_MESSAGE)
            .performScrollTo()
            .assertTextContains(string(R.string.gear_stat_rectangle_error_required))
        assertNull(submitted)
    }

    @Test
    fun edge_otherSearchRejectsALandOnlyAreaOffline() {
        var submitted: CatchRecordDraft? = null
        setOfflineMapScreen(onSubmit = { submitted = it })
        openOtherSearchOffline()

        composeTestRule.onNodeWithTag(MapScreenTestTags.AUTOCOMPLETE_FIELD).performScrollTo().performTextInput("40E01")
        composeTestRule.onNodeWithTag(MapScreenTestTags.AUTOCOMPLETE_SAVE_ACTION).performScrollTo().performClick()

        composeTestRule.onNodeWithText(string(R.string.gear_stat_rectangle_error_not_found)).assertIsDisplayed()
        assertNull(submitted)
    }

    @Test
    fun edge_otherSearchAcceptsLowercaseCodeAndNormalisesItOffline() {
        var submitted: CatchRecordDraft? = null
        setOfflineMapScreen(onSubmit = { submitted = it })
        openOtherSearchOffline()

        composeTestRule.onNodeWithTag(MapScreenTestTags.AUTOCOMPLETE_FIELD).performScrollTo().performTextInput("38e98")
        composeTestRule.onNodeWithTag(MapScreenTestTags.AUTOCOMPLETE_SAVE_ACTION).performScrollTo().performClick()

        assertEquals("38E98", submitted?.gearUses?.single()?.statisticalSubRectangleCode)
    }

    @Test
    fun edge_otherSearchShowsNoSuggestionsForOneCharacterOffline() {
        setOfflineMapScreen()
        openOtherSearchOffline()

        composeTestRule.onNodeWithTag(MapScreenTestTags.AUTOCOMPLETE_FIELD).performScrollTo().performTextInput("3")

        composeTestRule.onNodeWithTag("${MapScreenTestTags.SUGGESTION_PREFIX}_0").assertDoesNotExist()
    }

    @Test
    fun edge_otherSearchWithNoMatchesSaysSoOffline() {
        setOfflineMapScreen()
        openOtherSearchOffline()

        composeTestRule.onNodeWithTag(MapScreenTestTags.AUTOCOMPLETE_FIELD).performScrollTo().performTextInput("99Z")

        composeTestRule.onNodeWithTag(MapScreenTestTags.NO_MATCHES).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun edge_otherIsStillReachableWhenMapDataFailsToLoadOffline() {
        setOfflineMapScreen(mapStatus = UiStatus.Error(message = "Map unavailable", isRetryable = true))

        assertOfflineBannerShown()
        composeTestRule.onNodeWithTag(MapScreenTestTags.GRID_OTHER_ACTION).performScrollTo().assertIsDisplayed()
    }

    private fun openOtherSearchOffline() {
        openSuggestedList()
        radio(seaAreas.size).performClick()
    }

    @Test
    fun edge_portWithNoSelectionShowsValidationOffline() {
        var selection: PortSelection? = null
        setOfflinePortStep(favourites = emptyList()) { selection = it }

        composeTestRule.onNodeWithTag(DeparturePortScreenTestTags.SAVE_ACTION).performClick()

        composeTestRule
            .onNodeWithTag(DeparturePortScreenTestTags.ERROR_MESSAGE)
            .assertTextContains(string(R.string.port_selection_error))
        assertNull(selection)
    }

    @Test
    fun edge_portSearchWithNoMatchesSaysSoOffline() {
        setOfflinePortStep(favourites = emptyList()) {}

        composeTestRule.onNodeWithTag(DeparturePortScreenTestTags.AUTOCOMPLETE_FIELD).performTextInput("Zzz")

        composeTestRule.onNodeWithTag(DeparturePortScreenTestTags.NO_MATCHES).assertIsDisplayed()
    }

    @Test
    fun edge_retainedCatchAnswerIsKeptWhenResumingOffline() {
        var submitted: Boolean? = null
        composeTestRule.setContent {
            OfflineStep {
                NotLandedStraightAwayDecisionScreenContent(initialValue = true, onSubmit = { submitted = it })
            }
        }

        composeTestRule
            .onNodeWithTag(NotLandedStraightAwayDecisionScreenTestTags.SAVE_ACTION)
            .performScrollTo()
            .performClick()

        assertEquals(true, submitted)
    }

    @Test
    fun edge_checkTripEndDateFromLateWarningDoesNotSubmitOffline() {
        var checked = false
        var proceeded = false
        composeTestRule.setContent {
            OfflineStep {
                LateSubmissionWarningScreenContent(
                    onCheckTripEndDate = { checked = true },
                    onSubmit = { proceeded = true },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(LateSubmissionWarningScreenTestTags.CHECK_TRIP_END_DATE_LINK)
            .performFirstLinkClick()

        assertTrue(checked)
        assertFalse(proceeded)
    }

    private companion object {
        const val OFFLINE_BANNER = "offline_banner"
    }
}
