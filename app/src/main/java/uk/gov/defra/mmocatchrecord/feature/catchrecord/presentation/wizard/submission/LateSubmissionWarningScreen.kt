package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.submission

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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.GdsLinkAction
import uk.gov.defra.mmocatchrecord.common.design.PrimaryActionButton
import uk.gov.defra.mmocatchrecord.common.design.Spacing
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowEvent
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewModel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordWizardScaffold
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardErrorState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardLoadingState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardStep
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.nextWizardStepForDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.resolveSubmissionStep

object LateSubmissionWarningScreenTestTags {
    const val SCREEN = "late_submission_warning_screen"
    const val CHECK_TRIP_END_DATE_LINK = "late_submission_warning_check_trip_end_date_link"
    const val SAVE_ACTION = "late_submission_warning_save_action"
    const val ERROR_MESSAGE = "late_submission_warning_error_message"
}

/**
 * Phase 8, screen 1 (conditional): shown only when [nextWizardStepForDraft]/[resolveSubmissionStep]
 * determines the record is being submitted more than 24 hours after the trip's return date (see
 * [LateSubmissionSupport]). "Check the trip end date" deep-links back to [WizardStep.ReturnDate] without
 * saving anything (a pure "go look at this again" navigation, mirroring the check-your-answers screen's
 * "Change" links); "Save and continue" marks the warning acknowledged on the draft — see
 * [CatchRecordDraft.lateSubmissionWarningAcknowledged] — so re-deriving the next step for the (otherwise
 * unchanged) draft does not loop back to this same screen.
 *
 * The days-since-return figure in the title is (re)computed from `System.currentTimeMillis()` at
 * composition time — close enough in practice to the value [resolveSubmissionStep] used moments earlier to
 * route here, since both only need day-level precision.
 */
@Suppress("FunctionNaming")
@Composable
fun LateSubmissionWarningScreen(
    viewModel: CatchRecordFlowViewModel,
    onNavigate: (WizardStep) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nowEpochMillis = System.currentTimeMillis()
    val draft = (state.status as? UiStatus.Content)?.value
    val daysSinceReturn = draft?.returnDate?.let { LateSubmissionSupport.daysSinceReturn(it, nowEpochMillis) } ?: 0
    CatchRecordWizardScaffold(
        screenTestTag = LateSubmissionWarningScreenTestTags.SCREEN,
        title = pluralStringResource(R.plurals.late_submission_warning_title, daysSinceReturn, daysSinceReturn),
        onBack = onBack,
        modifier = modifier,
    ) {
        when (val status = state.status) {
            UiStatus.Idle, UiStatus.Loading -> WizardLoadingState()
            is UiStatus.Error ->
                WizardErrorState(
                    message = status.message,
                    testTag = LateSubmissionWarningScreenTestTags.ERROR_MESSAGE,
                    isRetryable = status.isRetryable,
                    onRetry = { viewModel.dispatch(CatchRecordFlowEvent.Retry) },
                )
            is UiStatus.Content ->
                LateSubmissionWarningScreenContent(
                    onCheckTripEndDate = { onNavigate(WizardStep.ReturnDate) },
                    onSubmit = {
                        val updatedDraft = status.value.copy(lateSubmissionWarningAcknowledged = true)
                        val nextStep = resolveSubmissionStep(updatedDraft, nowEpochMillis)
                        viewModel.dispatch(CatchRecordFlowEvent.SaveAndContinue(updatedDraft, nextStep))
                        onNavigate(nextStep)
                    },
                )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
fun LateSubmissionWarningScreenContent(
    onCheckTripEndDate: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(text = stringResource(R.string.late_submission_warning_body), style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            GdsLinkAction(
                text = stringResource(R.string.late_submission_warning_check_trip_end_date),
                onClick = onCheckTripEndDate,
                testTag = LateSubmissionWarningScreenTestTags.CHECK_TRIP_END_DATE_LINK,
            )
            Text(
                text = " " + stringResource(R.string.late_submission_warning_check_trip_end_date_suffix),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        PrimaryActionButton(
            text = stringResource(R.string.save_and_continue),
            onClick = onSubmit,
            modifier = Modifier.testTag(LateSubmissionWarningScreenTestTags.SAVE_ACTION),
        )
    }
}
