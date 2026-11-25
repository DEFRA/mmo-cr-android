@file:Suppress("detekt.FunctionNaming")

package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.gov.defra.mmocatchrecord.R

/**
 * GOV.UK notification-banner offline notice with a recovery action; a polite live region and named pane
 * (WCAG 4.1.3).
 */
@Composable
fun GdsOfflineNotice(
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "gds_offline_notice",
    title: String = stringResource(R.string.gds_offline_notice_title),
    message: String = stringResource(R.string.gds_offline_notice_message),
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag(testTag)
                .border(BorderStroke(5.dp, MmoColors.GovBlue))
                .semantics { paneTitle = title },
    ) {
        Column(
            modifier =
                Modifier
                    .testTag("${testTag}_status")
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(
                text = title,
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MmoColors.White,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MmoColors.GovBlue)
                        .padding(horizontal = Spacing.s, vertical = Spacing.xs)
                        .semantics { heading() },
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge.copy(color = MmoColors.Text),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MmoColors.White)
                        .padding(horizontal = Spacing.s, vertical = Spacing.s),
            )
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MmoColors.White)
                    .padding(start = Spacing.s, end = Spacing.s, bottom = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            SecondaryActionButton(
                text = actionLabel,
                onClick = onAction,
                modifier = Modifier.testTag("${testTag}_action"),
            )
        }
    }
}
