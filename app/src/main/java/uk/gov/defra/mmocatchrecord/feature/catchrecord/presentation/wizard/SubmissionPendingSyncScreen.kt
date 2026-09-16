package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.GdsLinkAction
import uk.gov.defra.mmocatchrecord.common.design.GdsResultBanner
import uk.gov.defra.mmocatchrecord.common.design.GdsResultBannerVariant
import uk.gov.defra.mmocatchrecord.common.design.GdsWarningText
import uk.gov.defra.mmocatchrecord.common.design.PrimaryActionButton
import uk.gov.defra.mmocatchrecord.common.design.Spacing
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft

object SubmissionPendingSyncScreenTestTags {
    const val SCREEN = "submission_pending_sync_screen"
    const val BANNER = "submission_pending_sync_banner"
    const val REFERENCE = "submission_pending_sync_reference"
    const val CHECK_RECORDS_LINK = "submission_pending_sync_check_records_link"
    const val WARNING = "submission_pending_sync_warning"
    const val VIEW_RECORDS_ACTION = "submission_pending_sync_view_records_action"
    const val ERROR_MESSAGE = "submission_pending_sync_error_message"
}

/**
 * Phase 8, screen 4: shown when the draft was submitted while offline (or the online attempt itself
 * failed) — [uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus.PendingSync], queued
 * for background sync via `CatchRecordSyncWorker` (see ADR 0009). "View your catch records"/"Check your
 * catch records" both use the same placeholder destination as [SubmissionSuccessScreen] (no "my records"
 * screen exists yet — out of scope for Phase 8).
 */
@Suppress("FunctionNaming")
@Composable
fun SubmissionPendingSyncScreen(
    viewModel: CatchRecordFlowViewModel,
    onViewRecords: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CatchRecordWizardScaffold(
        screenTestTag = SubmissionPendingSyncScreenTestTags.SCREEN,
        title = "",
        onBack = onBack,
        modifier = modifier,
    ) {
        when (val status = state.status) {
            UiStatus.Idle, UiStatus.Loading -> WizardLoadingState()
            is UiStatus.Error -> WizardErrorState(status.message, SubmissionPendingSyncScreenTestTags.ERROR_MESSAGE)
            is UiStatus.Content ->
                SubmissionPendingSyncScreenContent(draft = status.value, onViewRecords = onViewRecords)
        }
    }
}

@Composable
fun SubmissionPendingSyncScreenContent(
    draft: CatchRecordDraft,
    onViewRecords: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        GdsResultBanner(
            variant = GdsResultBannerVariant.PendingSync,
            title = stringResource(R.string.submission_pending_sync_banner_title),
            testTag = SubmissionPendingSyncScreenTestTags.BANNER,
        )
        draft.catchRecordReference?.let { reference ->
            Text(
                text = stringResource(R.string.submission_reference_label, reference),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.testTag(SubmissionPendingSyncScreenTestTags.REFERENCE),
            )
        }
        Text(
            text = stringResource(R.string.submission_pending_sync_body_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        )
        Text(text = stringResource(R.string.submission_pending_sync_body), style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            GdsLinkAction(
                text = stringResource(R.string.submission_pending_sync_check_records),
                onClick = onViewRecords,
                testTag = SubmissionPendingSyncScreenTestTags.CHECK_RECORDS_LINK,
            )
            Text(
                text = " " + stringResource(R.string.submission_pending_sync_check_records_suffix),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        GdsWarningText(
            text = stringResource(R.string.submission_pending_sync_warning),
            testTag = SubmissionPendingSyncScreenTestTags.WARNING,
        )
        PrimaryActionButton(
            text = stringResource(R.string.view_catch_records_action),
            onClick = onViewRecords,
            modifier = Modifier.testTag(SubmissionPendingSyncScreenTestTags.VIEW_RECORDS_ACTION),
        )
    }
}
