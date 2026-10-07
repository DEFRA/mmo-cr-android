@file:Suppress("detekt.FunctionNaming", "detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.home.presentation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.AppLanguageProvider
import uk.gov.defra.mmocatchrecord.common.design.DebugSettingsSection
import uk.gov.defra.mmocatchrecord.common.design.GdsTopAppBar
import uk.gov.defra.mmocatchrecord.common.design.MmoBottomNavigationBar
import uk.gov.defra.mmocatchrecord.common.design.MmoColors
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.common.design.OfflineBanner
import uk.gov.defra.mmocatchrecord.common.design.RecordStatusTag
import uk.gov.defra.mmocatchrecord.common.design.RoyalCrestPlaceholder
import uk.gov.defra.mmocatchrecord.common.design.Spacing
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.core.connectivity.ConnectivityViewModel
import uk.gov.defra.mmocatchrecord.core.language.AppLanguageViewModel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import uk.gov.defra.mmocatchrecord.feature.home.domain.CatchRecordSummary
import uk.gov.defra.mmocatchrecord.feature.home.domain.HomeSummary

object HomeScreenTestTags {
    const val SCREEN = "home_feature_screen"
    const val SIGN_OUT_ACTION = "home_feature_sign_out_action"
    const val ERROR_MESSAGE = "home_feature_error_message"
    const val TAB_LIST = "home_tab_list"
}

/** Default [DebugSettingsSection] for previews/call sites not supplying a real one — renders nothing. */
private object NoOpDebugSettingsSectionPreview : DebugSettingsSection {
    @Composable
    override fun Render() = Unit
}

@Suppress("FunctionNaming")
@Composable
fun HomeScreen(
    onSignOut: () -> Unit,
    onCreateCatchRecord: () -> Unit,
    onResumeDraft: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    languageViewModel: AppLanguageViewModel = hiltViewModel(),
    connectivityViewModel: ConnectivityViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentLanguage by languageViewModel.language.collectAsStateWithLifecycle()
    val isOffline by connectivityViewModel.isOffline.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

    HomeScreenContent(
        state = state,
        currentLanguage = currentLanguage,
        onLanguageToggle = languageViewModel::toggleLanguage,
        isOffline = isOffline,
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it },
        onSignOut = onSignOut,
        onCreateCatchRecord = onCreateCatchRecord,
        onResumeDraft = onResumeDraft,
        onRetry = { draftId -> viewModel.dispatch(HomeEvent.RetrySubmission(draftId)) },
        onDismissOfflineMessage = { viewModel.dispatch(HomeEvent.OfflineRetryMessageShown) },
        onDismissSyncConfirmationMessage = { viewModel.dispatch(HomeEvent.SyncConfirmationMessageShown) },
        debugSettingsSection = viewModel.debugSettingsSection,
        modifier = modifier,
    )
}

/** Stateless screen body shared by [HomeScreen], previews and Robolectric tests — avoids duplicating the Scaffold. */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
fun HomeScreenContent(
    state: HomeViewState,
    currentLanguage: String,
    onLanguageToggle: () -> Unit,
    isOffline: Boolean,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onSignOut: () -> Unit,
    onCreateCatchRecord: () -> Unit,
    onResumeDraft: (String) -> Unit,
    modifier: Modifier = Modifier,
    onRetry: (String) -> Unit = {},
    onDismissOfflineMessage: () -> Unit = {},
    onDismissSyncConfirmationMessage: () -> Unit = {},
    debugSettingsSection: DebugSettingsSection = NoOpDebugSettingsSectionPreview,
) {
    AppLanguageProvider(language = currentLanguage) {
        var catchRecordsPage by rememberSaveable { mutableIntStateOf(0) }
        val catchRecordsHorizontalScrollState = rememberScrollState()
        Scaffold(
            topBar = {
                GdsTopAppBar(
                    currentLanguage = currentLanguage,
                    onLanguageToggle = onLanguageToggle,
                    onBackClick = { if (selectedTab == 0) onSignOut() else onTabSelected(0) },
                    showBackButton = selectedTab != 0,
                )
            },
            bottomBar = {
                MmoBottomNavigationBar(selectedItem = selectedTab, onItemClick = onTabSelected)
            },
            modifier = modifier.fillMaxSize().testTag(HomeScreenTestTags.SCREEN),
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().background(MmoColors.White).padding(innerPadding)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (isOffline) {
                        OfflineBanner()
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        when (selectedTab) {
                            0 ->
                                HomeTabContent(
                                    state = state,
                                    onSignOut = onSignOut,
                                    onCreateCatchRecord = onCreateCatchRecord,
                                    onResumeDraft = onResumeDraft,
                                    onRetry = onRetry,
                                    onDismissOfflineMessage = onDismissOfflineMessage,
                                    onDismissSyncConfirmationMessage = onDismissSyncConfirmationMessage,
                                    catchRecordsPage = catchRecordsPage,
                                    onCatchRecordsPageChange = { catchRecordsPage = it },
                                    catchRecordsHorizontalScrollState = catchRecordsHorizontalScrollState,
                                )
                            1 -> NotificationsTabContent()
                            2 -> SettingsTabContent(onSignOut = onSignOut, debugSettingsSection = debugSettingsSection)
                        }
                    }
                }
            }
        }
    }
}

/** A single top-level [LazyColumn] (R14): a nested `LazyColumn` cannot live inside `verticalScroll`. */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun HomeTabContent(
    state: HomeViewState,
    onSignOut: () -> Unit,
    onCreateCatchRecord: () -> Unit,
    onResumeDraft: (String) -> Unit,
    onRetry: (String) -> Unit,
    onDismissOfflineMessage: () -> Unit,
    onDismissSyncConfirmationMessage: () -> Unit,
    catchRecordsPage: Int,
    onCatchRecordsPageChange: (Int) -> Unit,
    catchRecordsHorizontalScrollState: ScrollState,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(Spacing.m).testTag(HomeScreenTestTags.TAB_LIST),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        item { ImportantBannerSection() }
        state.offlineRetryMessage?.let { message ->
            item { OfflineRetryMessage(message = message, onDismissed = onDismissOfflineMessage) }
        }
        state.syncConfirmationMessage?.let { message ->
            item { SyncConfirmationMessage(message = message, onDismissed = onDismissSyncConfirmationMessage) }
        }
        item { HeadingSection(onCreateCatchRecord = onCreateCatchRecord) }
        when (val status = state.status) {
            UiStatus.Idle, UiStatus.Loading -> item { LoadingIndicator() }
            is UiStatus.Error ->
                item {
                    Text(
                        text = status.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag(HomeScreenTestTags.ERROR_MESSAGE),
                    )
                }
            is UiStatus.Content ->
                catchRecordsListSection(
                    records = status.value.catchRecords,
                    onRecordClick = onResumeDraft,
                    onRetry = onRetry,
                    retryingIds = state.retryingIds,
                    currentPage = catchRecordsPage,
                    onPageChange = onCatchRecordsPageChange,
                    horizontalScrollState = catchRecordsHorizontalScrollState,
                )
        }
        item { HelpAccordionsSection() }
        item {
            Button(
                onClick = onSignOut,
                modifier =
                    Modifier
                        .testTag(HomeScreenTestTags.SIGN_OUT_ACTION)
                        .size(1.dp)
                        .background(Color.Transparent),
            ) {}
        }
        item {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.m), contentAlignment = Alignment.Center) {
                RoyalCrestPlaceholder()
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
fun NotificationsTabContent() {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.m),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(R.string.nav_notifications), style = MaterialTheme.typography.headlineMedium)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun HomeScreenPreview() {
    val sampleSummary =
        HomeSummary(
            signedInUserId = "stub-user",
            catchRecords =
                listOf(
                    CatchRecordSummary(
                        "1",
                        "MMO-REF-001",
                        "ACHILLES",
                        DmyDate(20, 11, 2020),
                        RecordStatusTag.Submitted,
                    ),
                    CatchRecordSummary(
                        "2",
                        "MMO-REF-002",
                        "ACHILLES",
                        DmyDate(18, 11, 2020),
                        RecordStatusTag.ReadyToSubmit,
                    ),
                    CatchRecordSummary("3", null, "ACHILLES", null, RecordStatusTag.Draft),
                    CatchRecordSummary(
                        "4",
                        "MMO-REF-004",
                        "ACHILLES",
                        DmyDate(15, 11, 2020),
                        RecordStatusTag.AwaitingSync,
                    ),
                ),
        )
    val state = HomeViewState(status = UiStatus.Content(sampleSummary))

    MmoTheme {
        HomeScreenContent(
            state = state,
            currentLanguage = "en",
            onLanguageToggle = {},
            isOffline = false,
            selectedTab = 0,
            onTabSelected = {},
            onSignOut = {},
            onCreateCatchRecord = {},
            onResumeDraft = {},
        )
    }
}
