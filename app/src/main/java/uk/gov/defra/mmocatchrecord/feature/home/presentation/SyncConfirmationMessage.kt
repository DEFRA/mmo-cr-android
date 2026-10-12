package uk.gov.defra.mmocatchrecord.feature.home.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import uk.gov.defra.mmocatchrecord.common.design.MmoColors
import uk.gov.defra.mmocatchrecord.common.design.Spacing

object SyncConfirmationMessageTestTags {
    const val MESSAGE = "sync_confirmation_message"
}

private const val SYNC_CONFIRMATION_MESSAGE_AUTO_DISMISS_MILLIS = 6_000L

/** Transient, self-dismissing FR9 "waiting record submitted" confirmation — see ADR 0014 Phase E. */
@Suppress("FunctionNaming")
@Composable
fun SyncConfirmationMessage(
    message: String,
    onDismissed: () -> Unit,
) {
    LaunchedEffect(message) {
        delay(SYNC_CONFIRMATION_MESSAGE_AUTO_DISMISS_MILLIS)
        onDismissed()
    }
    Surface(
        color = MmoColors.White,
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(SyncConfirmationMessageTestTags.MESSAGE)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge.copy(color = MmoColors.Text),
            modifier = Modifier.padding(Spacing.s),
        )
    }
}
