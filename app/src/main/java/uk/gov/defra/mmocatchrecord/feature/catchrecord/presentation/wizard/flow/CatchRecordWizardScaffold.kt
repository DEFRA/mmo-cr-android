@file:Suppress("detekt.FunctionNaming", "detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.AppLanguageProvider
import uk.gov.defra.mmocatchrecord.common.design.CustomWarningIcon
import uk.gov.defra.mmocatchrecord.common.design.GdsTopAppBar
import uk.gov.defra.mmocatchrecord.common.design.MmoColors
import uk.gov.defra.mmocatchrecord.common.design.SecondaryActionButton
import uk.gov.defra.mmocatchrecord.common.design.Spacing
import uk.gov.defra.mmocatchrecord.core.language.AppLanguage
import uk.gov.defra.mmocatchrecord.core.language.AppLanguageViewModel

data class WizardErrorSummaryItem(
    val message: String,
    val onClick: () -> Unit,
)

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
    content: @Composable ColumnScope.() -> Unit,
) {
    // hiltViewModel() is unavailable in @Preview/inspection composition (no Hilt component present) — see
    // every wizard screen's *_Preview composable, which already renders via the state-based overload rather
    // than a real Hilt ViewModel, for the same reason. ADR 0012: the language preference itself is
    // app-wide/DataStore-persisted; this is purely "how does a preview safely opt out of Hilt".
    val isPreview = LocalInspectionMode.current
    val currentLanguage: String
    val onLanguageToggle: () -> Unit
    if (isPreview) {
        currentLanguage = AppLanguage.ENGLISH
        onLanguageToggle = {}
    } else {
        val languageViewModel: AppLanguageViewModel = hiltViewModel()
        currentLanguage = languageViewModel.language.collectAsStateWithLifecycle().value
        onLanguageToggle = languageViewModel::toggleLanguage
    }
    val scrollState = rememberScrollState()

    AppLanguageProvider(language = currentLanguage) {
        Scaffold(
            topBar = {
                GdsTopAppBar(
                    currentLanguage = currentLanguage,
                    onLanguageToggle = onLanguageToggle,
                    onBackClick = onBack,
                )
            },
            modifier = modifier.fillMaxSize().testTag(screenTestTag),
        ) { innerPadding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            MmoColors.White,
                        ).verticalScroll(scrollState)
                        .padding(innerPadding)
                        .padding(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                if (title.isNotBlank()) {
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
                content()
            }
        }
    }
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
