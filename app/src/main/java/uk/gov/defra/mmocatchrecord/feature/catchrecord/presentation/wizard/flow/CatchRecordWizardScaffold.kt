@file:Suppress("detekt.FunctionNaming", "detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.CustomWarningIcon
import uk.gov.defra.mmocatchrecord.common.design.GdsTopAppBar
import uk.gov.defra.mmocatchrecord.common.design.MmoBottomNavigationBar
import uk.gov.defra.mmocatchrecord.common.design.MmoColors
import uk.gov.defra.mmocatchrecord.common.design.OfflineBanner
import uk.gov.defra.mmocatchrecord.common.design.RoyalCrestFooter
import uk.gov.defra.mmocatchrecord.common.design.SecondaryActionButton
import uk.gov.defra.mmocatchrecord.common.design.Spacing
import uk.gov.defra.mmocatchrecord.common.design.UnsavedChangesDialog
import uk.gov.defra.mmocatchrecord.core.connectivity.ConnectivityViewModel
import uk.gov.defra.mmocatchrecord.core.language.AppLanguage
import uk.gov.defra.mmocatchrecord.core.language.AppLanguageViewModel

data class WizardErrorSummaryItem(
    val message: String,
    val onClick: () -> Unit,
)

/** BR-XX (ADR 0014, Phase D) unsaved-changes guard config for [CatchRecordWizardScaffold], grouped to one parameter. */
data class WizardUnsavedChangesConfig(
    val hasUnsavedChanges: Boolean = false,
    val onDiscardChanges: () -> Unit = {},
)

/** Resolved language/connectivity state a wizard screen needs, from Hilt, [LocalWizardScaffoldState] or a preview default. */
data class WizardScaffoldState(
    val currentLanguage: String,
    val onLanguageToggle: () -> Unit,
    val isOffline: Boolean,
)

/**
 * Exit action for [CatchRecordWizardScaffold]'s bottom navigation tabs: `0` Home, `1` Notifications,
 * `2` Settings. [uk.gov.defra.mmocatchrecord.common.navigation.MmoNavHost] supplies the real
 * implementation (navigate to [uk.gov.defra.mmocatchrecord.common.navigation.HomeRoute] on that tab,
 * clearing the wizard graph); the default no-op keeps previews/tests that don't provide one safe.
 */
val LocalWizardBottomNavAction = staticCompositionLocalOf<(Int) -> Unit> { {} }

/**
 * Sentinel shared by two unrelated concerns, both meaning "not one of the three Home-shell tabs": the
 * wizard's bottom nav never shows a tab as selected (the wizard isn't any of the three), and a pending
 * "leave" confirmation of `null` target (back button) uses the same value rather than a second enum.
 */
private const val WIZARD_BACK_LEAVE_TARGET = -1

/**
 * Optional explicit [WizardScaffoldState] for [CatchRecordWizardScaffold]. `null` (the default, and always
 * the case in the running app) resolves it from the Hilt-backed view models. Instrumented tests that compose
 * a wizard screen inside a plain, non-Hilt `ComponentActivity` provide a fixed state here instead, since
 * `hiltViewModel()` requires an `@AndroidEntryPoint` host.
 */
val LocalWizardScaffoldState = staticCompositionLocalOf<WizardScaffoldState?> { null }

/**
 * Resolves [WizardScaffoldState] from [LocalWizardScaffoldState] when provided, otherwise from the real
 * Hilt-backed [AppLanguageViewModel]/[ConnectivityViewModel], or safe preview defaults when composed inside
 * `@Preview`/inspection mode (see [CatchRecordWizardScaffold] doc comment on `hiltViewModel()` availability).
 */
@Composable
internal fun rememberWizardScaffoldState(): WizardScaffoldState {
    LocalWizardScaffoldState.current?.let { return it }
    if (LocalInspectionMode.current) {
        return WizardScaffoldState(
            currentLanguage = AppLanguage.ENGLISH,
            onLanguageToggle = {},
            isOffline = false,
        )
    }
    val languageViewModel: AppLanguageViewModel = hiltViewModel()
    val connectivityViewModel: ConnectivityViewModel = hiltViewModel()
    return WizardScaffoldState(
        currentLanguage = languageViewModel.language.collectAsStateWithLifecycle().value,
        onLanguageToggle = languageViewModel::toggleLanguage,
        isOffline = connectivityViewModel.isOffline.collectAsStateWithLifecycle().value,
    )
}

/**
 * [title] renders as the page's `headlineLarge` heading — pass a blank string only for the (rare) Phase 8
 * submission-result screens whose own coloured [uk.gov.defra.mmocatchrecord.common.design.GdsResultBanner]
 * *is* the page heading (that banner's own text carries the `heading()` semantics instead), so no separate
 * duplicate heading is rendered above it.
 *
 * [referenceNumber], when non-null, is rendered as a small grey caption directly above [title] — the
 * catch-record reference (see [uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.catchRecordReference])
 * shown on every wizard screen once the draft exists, matching the confirmed screenshots. It is only shown
 * alongside a non-blank [title]: the Phase 8 submission-result screens (blank title, per above) already
 * surface the same reference inside their own [uk.gov.defra.mmocatchrecord.common.design.GdsResultBanner],
 * so showing it again here would duplicate it.
 */
@Suppress("FunctionNaming")
@Composable
fun CatchRecordWizardScaffold(
    screenTestTag: String,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    referenceNumber: String? = null,
    unsavedChanges: WizardUnsavedChangesConfig = WizardUnsavedChangesConfig(),
    content: @Composable ColumnScope.() -> Unit,
) {
    // hiltViewModel() is unavailable in @Preview/inspection composition (no Hilt component present) — see
    // every wizard screen's *_Preview composable, which already renders via the state-based overload rather
    // than a real Hilt ViewModel, for the same reason. ADR 0012: the language preference itself is
    // app-wide/DataStore-persisted; this is purely "how does a preview safely opt out of Hilt".
    val scaffoldState = rememberWizardScaffoldState()
    val scrollState = rememberScrollState()
    val onNavigateToTab = LocalWizardBottomNavAction.current
    val pendingLeaveTarget = rememberWizardLeaveTarget(unsavedChanges.hasUnsavedChanges)
    val attemptBack = {
        if (unsavedChanges.hasUnsavedChanges) pendingLeaveTarget.value = WIZARD_BACK_LEAVE_TARGET else onBack()
    }
    val attemptNavigateToTab = { tab: Int ->
        if (unsavedChanges.hasUnsavedChanges) pendingLeaveTarget.value = tab else onNavigateToTab(tab)
    }

    Scaffold(
        topBar = {
            GdsTopAppBar(
                currentLanguage = scaffoldState.currentLanguage,
                onLanguageToggle = scaffoldState.onLanguageToggle,
                onBackClick = attemptBack,
            )
        },
        bottomBar = {
            // selectedItem is always "none" — the wizard isn't any of the three Home-shell tabs.
            MmoBottomNavigationBar(selectedItem = WIZARD_BACK_LEAVE_TARGET, onItemClick = attemptNavigateToTab)
        },
        modifier = modifier.fillMaxSize().testTag(screenTestTag),
    ) { innerPadding ->
        Column(
            // innerPadding (which now reserves space for the bottom nav bar) must be applied before
            // verticalScroll, or the scrollable viewport stays full-screen and scrolled content can
            // render behind the bottom bar, swallowing its clicks.
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        MmoColors.White,
                    ).padding(innerPadding)
                    .verticalScroll(scrollState)
                    .padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            if (scaffoldState.isOffline) {
                OfflineBanner()
            }
            WizardTitleSection(title, referenceNumber, screenTestTag)
            content()
            RoyalCrestFooter(testTag = "${screenTestTag}_royal_crest")
        }
    }
    WizardUnsavedChangesDialogHost(pendingLeaveTarget, unsavedChanges, onBack, onNavigateToTab)
}

/** Renders [title] (and the optional [referenceNumber] caption above it) when [title] is non-blank — see the doc comment on [CatchRecordWizardScaffold] for when it's blank. */
@Composable
private fun WizardTitleSection(
    title: String,
    referenceNumber: String?,
    screenTestTag: String,
) {
    if (title.isBlank()) return
    if (referenceNumber != null) {
        Text(
            text = referenceNumber,
            style = MaterialTheme.typography.bodyLarge,
            color = MmoColors.Grey1,
            modifier = Modifier.testTag("${screenTestTag}_reference_number"),
        )
    }
    Text(
        text = title,
        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.semantics { heading() },
    )
}

/** BR-XX (ADR 0014, Phase D): [BackHandler] is called unconditionally, only `enabled` varies. */
@Composable
private fun rememberWizardLeaveTarget(hasUnsavedChanges: Boolean): MutableState<Int?> {
    val pendingLeaveTarget = rememberSaveable { mutableStateOf<Int?>(null) }
    BackHandler(enabled = hasUnsavedChanges) { pendingLeaveTarget.value = WIZARD_BACK_LEAVE_TARGET }
    return pendingLeaveTarget
}

/**
 * Renders [UnsavedChangesDialog] when a leave is pending, wired to discard-then-leave or stay. The pending
 * target is either [WIZARD_BACK_LEAVE_TARGET] (the back button/hardware back was pressed) or a Home-shell
 * tab index (a bottom nav tab was tapped) — both abandon the current unsaved step the same way.
 */
@Composable
private fun WizardUnsavedChangesDialogHost(
    pendingLeaveTarget: MutableState<Int?>,
    unsavedChanges: WizardUnsavedChangesConfig,
    onBack: () -> Unit,
    onNavigateToTab: (Int) -> Unit,
) {
    val target = pendingLeaveTarget.value ?: return
    UnsavedChangesDialog(
        onStay = { pendingLeaveTarget.value = null },
        onLeave = {
            pendingLeaveTarget.value = null
            unsavedChanges.onDiscardChanges()
            if (target == WIZARD_BACK_LEAVE_TARGET) onBack() else onNavigateToTab(target)
        },
    )
}

@Suppress("FunctionNaming")
@Composable
fun WizardLoadingState(modifier: Modifier = Modifier) {
    CircularProgressIndicator(color = MmoColors.GovBlue, modifier = modifier)
}

/**
 * Renders a wizard error: an icon **and** text together (never colour alone, per WCAG 2.2 AA), announced to
 * assistive technology via an assertive live region and given initial keyboard/TalkBack focus as soon as it
 * appears — so a screen-reader user is told about the failure immediately, without having to discover it by
 * exploring the screen. When [isRetryable] is true and [onRetry] is supplied, an accessible "Try again"
 * action is shown (see the finding "Retryable wizard errors need accessible Retry control, live region and
 * focus strategy"); a terminal, non-retryable error (e.g. a validation failure) shows no retry action, since
 * retrying the exact same operation cannot fix it.
 */
@Suppress("FunctionNaming")
@Composable
fun WizardErrorState(
    message: String,
    testTag: String,
    modifier: Modifier = Modifier,
    isRetryable: Boolean = false,
    onRetry: (() -> Unit)? = null,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(message) {
        focusRequester.requestFocus()
    }
    Column(
        modifier = modifier.fillMaxWidth().testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .focusable()
                    .semantics { liveRegion = LiveRegionMode.Assertive },
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.Top,
        ) {
            CustomWarningIcon(tint = MmoColors.ErrorRed, modifier = Modifier.size(Spacing.m))
            Text(
                text = message,
                color = MmoColors.ErrorRed,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (isRetryable && onRetry != null) {
            SecondaryActionButton(
                text = stringResource(R.string.wizard_error_retry_action),
                onClick = onRetry,
                modifier = Modifier.testTag("${testTag}_retry_action"),
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
fun WizardErrorSummary(
    title: String,
    items: List<WizardErrorSummaryItem>,
    focusRequester: FocusRequester,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    MmoColors.White,
                ).focusRequester(focusRequester)
                .focusable()
                .testTag(testTag)
                .padding(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MmoColors.ErrorRed,
        )
        items.forEach { item ->
            Text(
                text = item.message,
                color = MmoColors.Link,
                style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.Underline),
                modifier = Modifier.clickable(onClick = item.onClick).heightIn(min = Spacing.minTouchTarget),
            )
        }
    }
}
