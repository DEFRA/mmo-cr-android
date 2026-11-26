@file:Suppress("detekt.FunctionNaming")

package uk.gov.defra.mmocatchrecord.feature.passwordreset.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.CrownLogo
import uk.gov.defra.mmocatchrecord.common.design.GdsErrorSummary
import uk.gov.defra.mmocatchrecord.common.design.GdsErrorSummaryItem
import uk.gov.defra.mmocatchrecord.common.design.GdsLanguageToggleBar
import uk.gov.defra.mmocatchrecord.common.design.GdsOfflineOverlay
import uk.gov.defra.mmocatchrecord.common.design.MmoColors
import uk.gov.defra.mmocatchrecord.common.design.RoyalCrestPlaceholder
import uk.gov.defra.mmocatchrecord.common.design.Spacing
import uk.gov.defra.mmocatchrecord.core.language.AppLanguage
import uk.gov.defra.mmocatchrecord.core.language.AppLanguageViewModel

/** Compose test tags for the reset screens, kept in one place so tests don't hard-code strings. */
object ResetPasswordTestTags {
    const val EMAIL_SCREEN = "reset_password_email_screen"
    const val CODE_SCREEN = "reset_password_code_screen"
    const val NEW_PASSWORD_SCREEN = "reset_password_new_password_screen"
    const val SUCCESS_SCREEN = "reset_password_success_screen"
    const val HEADING = "reset_password_heading"
    const val GUIDANCE = "reset_password_guidance"
    const val EMAIL_FIELD = "reset_password_email_field"
    const val CODE_FIELD = "reset_password_code_field"
    const val NEW_PASSWORD_FIELD = "reset_password_new_password_field"
    const val CONFIRM_PASSWORD_FIELD = "reset_password_confirm_password_field"
    const val SUBMIT_ACTION = "reset_password_submit_action"
    const val SEND_NEW_CODE_ACTION = "reset_password_send_new_code_action"
    const val CODE_EXPIRY = "reset_password_code_expiry"
    const val SIGN_IN_ACTION = "reset_password_sign_in_action"
    const val ERROR_SUMMARY = "reset_password_error_summary"
    const val OFFLINE_NOTICE = "reset_password_offline_notice"
    const val LOADING = "reset_password_loading"
    const val BROWSER_HANDOFF_MESSAGE = "reset_password_browser_handoff_message"
    const val CANCEL_DIALOG = "reset_password_cancel_dialog"
    const val CANCEL_DIALOG_CONFIRM = "reset_password_cancel_dialog_confirm"
    const val CANCEL_DIALOG_DISMISS = "reset_password_cancel_dialog_dismiss"
}

/** The in-app language and toggle every reset screen's top bar needs. */
data class ResetChrome(
    val currentLanguage: String,
    val onLanguageToggle: () -> Unit,
)

/** Non-state callbacks shared by every reset screen; the defaults keep previews and tests terse. */
data class ResetScreenActions(
    val onBack: () -> Unit = {},
    val onRecheckConnection: () -> Unit = {},
    val onBrowserNoticeShown: () -> Unit = {},
)

/**
 * Optional explicit [ResetChrome]; `null` (always the case in the running app) resolves it from Hilt. Tests
 * that compose the nav graph in a plain `ComponentActivity` (no Hilt) provide a fixed value instead.
 */
internal val LocalResetChrome = staticCompositionLocalOf<ResetChrome?> { null }

@Composable
internal fun rememberResetChrome(): ResetChrome {
    LocalResetChrome.current?.let { return it }
    if (LocalInspectionMode.current) return ResetChrome(AppLanguage.ENGLISH) {}
    val languageViewModel: AppLanguageViewModel = hiltViewModel()
    return ResetChrome(
        currentLanguage = languageViewModel.language.collectAsStateWithLifecycle().value,
        onLanguageToggle = languageViewModel::toggleLanguage,
    )
}

/** One focus target per field so an error-summary link can move focus to the field it describes. */
internal class ResetFieldFocus {
    val summary = FocusRequester()
    private val targets = ResetField.entries.associateWith { FocusRequester() }

    fun of(field: ResetField): FocusRequester = targets.getValue(field)
}

/**
 * Shared frame of the four screens: top bar, offline notice, error summary above the heading (GOV.UK), the
 * content, then the loading and browser hand-off status. Back and system Back both call [onBack].
 */
@Composable
@Suppress("LongParameterList")
internal fun ResetPasswordScaffold(
    screenTestTag: String,
    title: String,
    state: ResetPasswordViewState,
    chrome: ResetChrome,
    actions: ResetScreenActions,
    modifier: Modifier = Modifier,
    focus: ResetFieldFocus = remember { ResetFieldFocus() },
    firstField: ResetField? = null,
    onBack: () -> Unit = actions.onBack,
    showHeading: Boolean = true,
    showLogo: Boolean = false,
    headingAlign: TextAlign = TextAlign.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Studio previews provide no back-dispatcher owner, so BackHandler would fail the render.
    if (!LocalInspectionMode.current) BackHandler(onBack = onBack)
    Box(modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize().testTag(screenTestTag),
            topBar = {
                GdsLanguageToggleBar(
                    currentLanguage = chrome.currentLanguage,
                    onLanguageToggle = chrome.onLanguageToggle,
                )
            },
        ) { innerPadding ->
            BoxWithConstraints(Modifier.padding(innerPadding).fillMaxSize().background(MmoColors.White)) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .heightIn(min = if (constraints.hasBoundedHeight) maxHeight else Dp.Unspecified)
                            .padding(horizontal = Spacing.xl, vertical = Spacing.s)
                            .semantics { paneTitle = title },
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    ResetErrorSummary(state = state, focus = focus, firstField = firstField)
                    if (showLogo) {
                        CrownLogo(Modifier.align(Alignment.CenterHorizontally).padding(bottom = Spacing.l))
                    }
                    if (showHeading) ResetHeading(title, headingAlign)
                    content()
                    ResetStatus(state = state, onBrowserNoticeShown = actions.onBrowserNoticeShown)
                    Spacer(Modifier.weight(1f))
                    RoyalCrestPlaceholder(Modifier.align(Alignment.CenterHorizontally).padding(bottom = Spacing.xxl))
                }
            }
        }
        if (state.isOffline) {
            GdsOfflineOverlay(
                actionLabel = stringResource(R.string.reset_password_offline_action),
                onAction = actions.onRecheckConnection,
                testTag = ResetPasswordTestTags.OFFLINE_NOTICE,
            )
        }
    }
}

@Composable
private fun ResetHeading(
    title: String,
    textAlign: TextAlign,
) {
    Text(
        text = title,
        textAlign = textAlign,
        style =
            MaterialTheme.typography.headlineLarge.copy(
                color = MmoColors.Text,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                lineHeight = 44.sp,
            ),
        modifier = Modifier.fillMaxWidth().testTag(ResetPasswordTestTags.HEADING).semantics { heading() },
    )
}

@Composable
private fun ResetErrorSummary(
    state: ResetPasswordViewState,
    focus: ResetFieldFocus,
    firstField: ResetField?,
) {
    val fieldItems =
        state.fieldErrors.map { error ->
            GdsErrorSummaryItem(error.resolve()) { focus.of(error.field).requestFocus() }
        }
    val pageItems =
        listOfNotNull(
            state.pageErrorRes?.let { res ->
                GdsErrorSummaryItem(stringResource(res)) { firstField?.let { focus.of(it).requestFocus() } }
            },
        )
    val items = fieldItems + pageItems
    if (items.isNotEmpty()) {
        GdsErrorSummary(
            items = items,
            focusRequester = focus.summary,
            testTag = ResetPasswordTestTags.ERROR_SUMMARY,
            focusTrigger = state.errorSequence,
        )
    }
}

@Composable
private fun ResetStatus(
    state: ResetPasswordViewState,
    onBrowserNoticeShown: () -> Unit,
) {
    if (state.isRedirectingToBrowser) {
        // Fires once the notice composes, so the repository knows TalkBack has had a chance to speak it.
        LaunchedEffect(Unit) { onBrowserNoticeShown() }
        Text(
            text = stringResource(R.string.reset_password_redirecting_to_browser),
            style = MaterialTheme.typography.bodyLarge.copy(color = MmoColors.Text),
            modifier =
                Modifier
                    .testTag(ResetPasswordTestTags.BROWSER_HANDOFF_MESSAGE)
                    .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    if (state.isLoading) {
        val loading = stringResource(R.string.reset_password_loading)
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(ResetPasswordTestTags.LOADING)
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = loading
                    },
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = MmoColors.GovBlue)
        }
    }
}
