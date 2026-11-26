@file:Suppress("detekt.FunctionNaming")

package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
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

/** GOV.UK blue offline panel with a recovery action; a polite live region and named pane (WCAG 4.1.3). */
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
                .background(MmoColors.GovBlue)
                .padding(Spacing.m)
                .semantics { paneTitle = title },
        verticalArrangement = Arrangement.spacedBy(Spacing.xxl),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            modifier =
                Modifier
                    .testTag("${testTag}_status")
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(
                text = title,
                style =
                    MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MmoColors.White,
                    ),
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge.copy(color = MmoColors.White),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        GdsButtonFrame(
            onClick = onAction,
            colors =
                GdsButtonColors(
                    fill = MmoColors.White,
                    pressedFill = MmoColors.Background,
                    content = MmoColors.GovBlue,
                    edge = MmoColors.Text,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("${testTag}_action"),
        ) { contentColor ->
            Text(text = actionLabel, style = MaterialTheme.typography.bodyLarge.copy(color = contentColor))
        }
    }
}

private const val OFFLINE_SCRIM_ALPHA = 0.5f
private val OFFLINE_NOTICE_MIN_HEIGHT = 500.dp
private val OFFLINE_NOTICE_TOP_OFFSET = 160.dp

/** Full-size dimming scrim that blocks the content beneath, with [GdsOfflineNotice] floating over it. */
@Composable
fun BoxScope.GdsOfflineOverlay(
    actionLabel: String,
    onAction: () -> Unit,
    testTag: String = "gds_offline_notice",
) {
    Box(
        modifier =
            Modifier
                .matchParentSize()
                .background(MmoColors.Text.copy(alpha = OFFLINE_SCRIM_ALPHA))
                .pointerInput(Unit) { detectTapGestures { } },
    )
    GdsOfflineNotice(
        actionLabel = actionLabel,
        onAction = onAction,
        testTag = testTag,
        modifier =
            Modifier
                .align(Alignment.TopCenter)
                .padding(start = Spacing.s, end = Spacing.s, top = OFFLINE_NOTICE_TOP_OFFSET)
                .heightIn(min = OFFLINE_NOTICE_MIN_HEIGHT),
    )
}
