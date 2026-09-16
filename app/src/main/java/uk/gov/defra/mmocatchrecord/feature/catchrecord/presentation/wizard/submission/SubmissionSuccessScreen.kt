package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.submission

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.GdsResultBanner
import uk.gov.defra.mmocatchrecord.common.design.GdsResultBannerVariant
import uk.gov.defra.mmocatchrecord.common.design.PrimaryActionButton
import uk.gov.defra.mmocatchrecord.common.design.Spacing
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewModel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordWizardScaffold
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardErrorState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardLoadingState

object SubmissionSuccessScreenTestTags {
    const val SCREEN = "submission_success_screen"
    const val BANNER = "submission_success_banner"
    const val REFERENCE = "submission_success_reference"
    const val VIEW_RECORDS_ACTION = "submission_success_view_records_action"
    const val ERROR_MESSAGE = "submission_success_error_message"
}

/**
 * Phase 8, screen 3: shown after [CatchRecordFlowViewModel.acceptDeclarationAndSubmit] submits the draft
 * online successfully — [uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus.Submitted].
 *
 * "View your catch records" is wired to [onViewRecords] as a **placeholder** destination (no "my records"
 * list screen exists yet in this app — out of scope for Phase 8, per the approved task instruction) — see
 * `MmoNavHost`'s wiring of this screen, which navigates to the app's existing Home screen.
 */
@Suppress("FunctionNaming")
@Composable
fun SubmissionSuccessScreen(
    viewModel: CatchRecordFlowViewModel,
    onViewRecords: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CatchRecordWizardScaffold(
        screenTestTag = SubmissionSuccessScreenTestTags.SCREEN,
        title = "",
        onBack = onBack,
        modifier = modifier,
    ) {
        when (val status = state.status) {
            UiStatus.Idle, UiStatus.Loading -> WizardLoadingState()
            is UiStatus.Error -> WizardErrorState(status.message, SubmissionSuccessScreenTestTags.ERROR_MESSAGE)
            is UiStatus.Content ->
                SubmissionSuccessScreenContent(draft = status.value, onViewRecords = onViewRecords)
        }
    }
}

@Composable
fun SubmissionSuccessScreenContent(
    draft: CatchRecordDraft,
    onViewRecords: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        GdsResultBanner(
            variant = GdsResultBannerVariant.Success,
            title = stringResource(R.string.submission_success_banner_title),
            testTag = SubmissionSuccessScreenTestTags.BANNER,
        )
        draft.catchRecordReference?.let { reference ->
            Text(
                text = stringResource(R.string.submission_reference_label, reference),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.testTag(SubmissionSuccessScreenTestTags.REFERENCE),
            )
        }
        Text(
            text = stringResource(R.string.submission_success_what_happens_next_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        )
        listOf(
            R.string.submission_success_bullet_received,
            R.string.submission_success_bullet_email,
            R.string.submission_success_bullet_view,
            R.string.submission_success_bullet_save_reference,
        ).forEach { bulletRes ->
            Text(text = "• " + stringResource(bulletRes), style = MaterialTheme.typography.bodyLarge)
        }
        PrimaryActionButton(
            text = stringResource(R.string.view_catch_records_action),
            onClick = onViewRecords,
            modifier = Modifier.testTag(SubmissionSuccessScreenTestTags.VIEW_RECORDS_ACTION),
        )
    }
}
