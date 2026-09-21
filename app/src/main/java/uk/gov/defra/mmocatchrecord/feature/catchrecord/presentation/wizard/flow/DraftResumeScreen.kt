@file:Suppress("detekt.FunctionNaming", "detekt.LongMethod")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.GdsRadioGroup
import uk.gov.defra.mmocatchrecord.common.design.GdsRadioOption
import uk.gov.defra.mmocatchrecord.common.design.PrimaryActionButton
import uk.gov.defra.mmocatchrecord.common.design.Spacing
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus

object DraftResumeScreenTestTags {
    const val SCREEN = "draft_resume_screen"
    const val OPTION_PREFIX = "draft_resume_option"
    const val SAVE_ACTION = "draft_resume_save_action"
    const val ERROR_MESSAGE = "draft_resume_error_message"
    const val CONFIRM_DIALOG = "draft_resume_confirm_dialog"
    const val CONFIRM_DELETE = "draft_resume_confirm_delete"
    const val CANCEL_DELETE = "draft_resume_cancel_delete"
}

private const val COMPLETE_ACTION = "complete"
private const val DELETE_ACTION = "delete"

@Suppress("FunctionNaming")
@Composable
fun DraftResumeScreen(
    viewModel: CatchRecordFlowViewModel,
    onNavigate: (WizardStep) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CatchRecordWizardScaffold(
        screenTestTag = DraftResumeScreenTestTags.SCREEN,
        title = stringResource(R.string.draft_resume_title),
        onBack = onBack,
        modifier = modifier,
        referenceNumber = state.catchRecordReference,
    ) {
        when (val status = state.status) {
            UiStatus.Loading -> WizardLoadingState()
            is UiStatus.Error ->
                WizardErrorState(
                    message = status.message,
                    testTag = DraftResumeScreenTestTags.ERROR_MESSAGE,
                    isRetryable = status.isRetryable,
                    onRetry = { viewModel.dispatch(CatchRecordFlowEvent.Retry) },
                )
            UiStatus.Idle -> WizardLoadingState()
            is UiStatus.Content ->
                DraftResumeScreenContent(
                    onResume = {
                        viewModel.dispatch(CatchRecordFlowEvent.ResumeDraft)
                        onNavigate(nextWizardStepForDraft(status.value))
                    },
                    onDeleteConfirmed = {
                        viewModel.dispatch(CatchRecordFlowEvent.DeleteDraft)
                        onNavigate(WizardStep.VesselSelection)
                    },
                )
        }
    }
}

@Suppress("FunctionNaming", "LongMethod")
@Composable
fun DraftResumeScreenContent(
    onResume: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedAction by rememberSaveable { mutableStateOf<String?>(null) }
    var showValidationError by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        GdsRadioGroup(
            options =
                listOf(
                    GdsRadioOption(COMPLETE_ACTION, stringResource(R.string.draft_resume_complete_option)),
                    GdsRadioOption(DELETE_ACTION, stringResource(R.string.draft_resume_delete_option)),
                ),
            selectedOptionId = selectedAction,
            onOptionSelected = {
                selectedAction = it
                showValidationError = false
            },
            optionTestTagPrefix = DraftResumeScreenTestTags.OPTION_PREFIX,
        )
        if (showValidationError) {
            Text(
                text = stringResource(R.string.wizard_select_action_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(DraftResumeScreenTestTags.ERROR_MESSAGE),
            )
        }
        PrimaryActionButton(
            text = stringResource(R.string.save_and_continue),
            onClick = {
                when (selectedAction) {
                    COMPLETE_ACTION -> onResume()
                    DELETE_ACTION -> showDeleteConfirmation = true
                    null -> showValidationError = true
                }
            },
            modifier = Modifier.testTag(DraftResumeScreenTestTags.SAVE_ACTION),
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            modifier = Modifier.testTag(DraftResumeScreenTestTags.CONFIRM_DIALOG),
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.draft_delete_confirmation_title)) },
            text = { Text(stringResource(R.string.draft_delete_confirmation_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDeleteConfirmed()
                    },
                    modifier = Modifier.testTag(DraftResumeScreenTestTags.CONFIRM_DELETE),
                ) { Text(stringResource(R.string.draft_delete_confirmation_confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmation = false },
                    modifier = Modifier.testTag(DraftResumeScreenTestTags.CANCEL_DELETE),
                ) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
