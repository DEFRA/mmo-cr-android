package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import uk.gov.defra.mmocatchrecord.R

object UnsavedChangesDialogTestTags {
    const val DIALOG = "unsaved_changes_dialog"
    const val LEAVE_ACTION = "unsaved_changes_leave_action"
    const val STAY_ACTION = "unsaved_changes_stay_action"
}

/**
 * BR-XX's unsaved-data warning (see ADR 0014, Phase D): a native Material 3 [AlertDialog] rather than a GDS
 * question page, since the trigger is a back gesture and a full interruption page would itself need back
 * interception. [onStay] is also invoked for an outside-tap/system-dismiss ([DialogProperties]'s default
 * `dismissOnBackPress`/`dismissOnClickOutside`), since both mean "stay on this page", never "leave".
 */
@Composable
fun UnsavedChangesDialog(
    onStay: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier.testTag(UnsavedChangesDialogTestTags.DIALOG),
        onDismissRequest = onStay,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
        title = { Text(stringResource(R.string.unsaved_changes_dialog_title)) },
        text = { Text(stringResource(R.string.unsaved_changes_dialog_body)) },
        confirmButton = {
            TextButton(
                onClick = onLeave,
                modifier =
                    Modifier
                        .testTag(UnsavedChangesDialogTestTags.LEAVE_ACTION)
                        .heightIn(min = Spacing.minTouchTarget),
            ) { Text(stringResource(R.string.unsaved_changes_leave_action)) }
        },
        dismissButton = {
            TextButton(
                onClick = onStay,
                modifier =
                    Modifier
                        .testTag(UnsavedChangesDialogTestTags.STAY_ACTION)
                        .heightIn(min = Spacing.minTouchTarget),
            ) { Text(stringResource(R.string.unsaved_changes_stay_action)) }
        },
    )
}
