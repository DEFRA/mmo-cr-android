package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.submission

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.GdsResultBanner
import uk.gov.defra.mmocatchrecord.common.design.GdsResultBannerVariant
import uk.gov.defra.mmocatchrecord.common.design.MmoColors
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.common.design.PrimaryActionButton
import uk.gov.defra.mmocatchrecord.common.design.Spacing
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SubmissionFailureReason
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowEvent
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewModel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordWizardScaffold
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardErrorState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardLoadingState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardStep

object SubmissionFailedScreenTestTags {
    const val SCREEN = "submission_failed_screen"
    const val BANNER = "submission_failed_banner"
    const val RETRY_ACTION = "submission_failed_retry_action"
    const val ERROR_MESSAGE = "submission_failed_error_message"
}

/** ADR 0026: terminal online-submission failure — "Could not send"; "Try again" re-queues for sync. */
@Suppress("FunctionNaming")
@Composable
fun SubmissionFailedScreen(
    viewModel: CatchRecordFlowViewModel,
    onNavigate: (WizardStep) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // "Try again" re-queues the draft asynchronously (see `retryFailedSubmission`); once the ViewModel
    // flips currentStep to SubmissionPendingSync, navigate there — mirrors CheckYourAnswersScreen's effect.
    var hasNavigatedAfterRetry by remember { mutableStateOf(false) }
    LaunchedEffect(state.currentStep) {
        if (state.currentStep == WizardStep.SubmissionPendingSync && !hasNavigatedAfterRetry) {
            hasNavigatedAfterRetry = true
            onNavigate(state.currentStep)
        }
    }

    CatchRecordWizardScaffold(
        screenTestTag = SubmissionFailedScreenTestTags.SCREEN,
        title = "",
        onBack = onBack,
        modifier = modifier,
    ) {
        when (val status = state.status) {
            UiStatus.Idle, UiStatus.Loading -> WizardLoadingState()
            is UiStatus.Error ->
                WizardErrorState(
                    message = status.message,
                    testTag = SubmissionFailedScreenTestTags.ERROR_MESSAGE,
                    isRetryable = status.isRetryable,
                    onRetry = { viewModel.dispatch(CatchRecordFlowEvent.Retry) },
                )
            is UiStatus.Content ->
                SubmissionFailedScreenContent(
                    draft = status.value,
                    onRetry = { viewModel.dispatch(CatchRecordFlowEvent.RetryFailedSubmission) },
                )
        }
    }
}

/** Reason-specific body copy for the "Could not send" screen — falls back to the generic message for an
 *  unrecognised or absent (older-persisted) reason string, never blocking "Try again" (ADR 0026). */
@Composable
private fun submissionFailedBody(reasonName: String?): String =
    when (reasonName) {
        SubmissionFailureReason.NeedsSignIn.name -> stringResource(R.string.submission_failed_body_needs_sign_in)
        SubmissionFailureReason.NotAuthorisedForVessel.name ->
            stringResource(R.string.submission_failed_body_not_authorised)
        SubmissionFailureReason.Rejected.name, SubmissionFailureReason.DataProblem.name ->
            stringResource(R.string.submission_failed_body_rejected)
        SubmissionFailureReason.TooLarge.name -> stringResource(R.string.submission_failed_body_too_large)
        else -> stringResource(R.string.submission_failed_body)
    }

@Composable
fun SubmissionFailedScreenContent(
    draft: CatchRecordDraft,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        GdsResultBanner(
            variant = GdsResultBannerVariant.Failed,
            title = stringResource(R.string.submission_failed_banner_title),
            testTag = SubmissionFailedScreenTestTags.BANNER,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            draft.catchRecordReference?.let { reference ->
                Text(
                    text = stringResource(R.string.submission_reference_label, reference),
                    style =
                        MaterialTheme.typography.bodyLarge.copy(
                            color = MmoColors.White,
                            fontWeight = FontWeight.Bold,
                        ),
                )
            }
        }
        Text(
            text = stringResource(R.string.submission_failed_body_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        )
        Text(text = submissionFailedBody(draft.submissionFailureReason), style = MaterialTheme.typography.bodyLarge)
        // Sign-in is not wired as a distinct action here (NeedsSignIn still uses "Try again"): routing to
        // SignInRoute cleanly needs RootEvent.SignedOut via SessionCoordinator (MSAL), which is out of scope.
        PrimaryActionButton(
            text = stringResource(R.string.submission_failed_retry_action),
            onClick = onRetry,
            modifier = Modifier.testTag(SubmissionFailedScreenTestTags.RETRY_ACTION),
        )
    }
}

@Preview(showBackground = true)
@Suppress("FunctionNaming")
@Composable
fun SubmissionFailedScreenContentPreview() {
    MmoTheme {
        SubmissionFailedScreenContent(
            draft =
                CatchRecordDraft(
                    id = "draft-1",
                    vesselId = "vessel-1",
                    modifiedAtEpochMillis = 0L,
                    catchRecordReference = "GBR-B14974-270726-150815",
                ),
            onRetry = {},
        )
    }
}
