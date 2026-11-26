@file:Suppress("detekt.FunctionNaming")

package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import uk.gov.defra.mmocatchrecord.R

/** One error in a [GdsErrorSummary]; [onClick] moves focus to the field the message is about. */
data class GdsErrorSummaryItem(
    val message: String,
    val onClick: () -> Unit,
)

/**
 * GOV.UK error summary: takes focus when shown (and when [focusTrigger] changes) and is an
 * assertive live region (WCAG 3.3.1, 4.1.3).
 */
@Composable
fun GdsErrorSummary(
    items: List<GdsErrorSummaryItem>,
    focusRequester: FocusRequester,
    testTag: String,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.error_summary_title),
    focusTrigger: Any? = null,
) {
    LaunchedEffect(items.map { it.message }, focusTrigger) {
        if (items.isNotEmpty()) focusRequester.requestFocus()
    }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag(testTag)
                .border(BorderStroke(5.dp, MmoColors.ErrorRed))
                .background(MmoColors.White)
                .focusRequester(focusRequester)
                .focusable()
                .semantics { liveRegion = LiveRegionMode.Assertive }
                .padding(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = MmoColors.Text),
            modifier = Modifier.semantics { heading() },
        )
        items.forEachIndexed { index, item ->
            Text(
                text = item.message,
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        color = MmoColors.ErrorRed,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Spacing.minTouchTarget)
                        .testTag("${testTag}_item_$index")
                        .clickable(role = Role.Button, onClick = item.onClick),
            )
        }
    }
}
