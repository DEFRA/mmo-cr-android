@file:Suppress("detekt.FunctionNaming")

package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Which confirmation banner colour/icon to show — see [GdsResultBanner]. */
enum class GdsResultBannerVariant {
    /** Green header, text-only — the record was submitted online successfully. */
    Success,

    /** Blue header + info-circle icon — the record was recorded and is queued for background sync. */
    PendingSync,
}

/**
 * GOV.UK-style confirmation banner (coloured header + white body), used by the Phase 8 submission-result
 * screens. The [title] text itself differs between variants ("submitted" vs "recorded"), so meaning is
 * never conveyed by colour alone even though only [GdsResultBannerVariant.PendingSync] pairs its text
 * with an icon — per WCAG 2.2 AA, the banner still reads correctly to a colour-blind user or when
 * rendered in greyscale.
 *
 * [content], when supplied, renders additional white text inside the same coloured panel below the title
 * (e.g. the catch record reference on the pending-sync screen) rather than as separate black body copy —
 * matching the GOV.UK confirmation-page pattern of keeping the reference inside the coloured banner.
 */
@Composable
fun GdsResultBanner(
    variant: GdsResultBannerVariant,
    title: String,
    testTag: String,
    modifier: Modifier = Modifier,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val headerColor = if (variant == GdsResultBannerVariant.Success) MmoColors.SuccessGreen else MmoColors.GovBlue
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag(testTag)
                .background(headerColor)
                .padding(horizontal = Spacing.m, vertical = Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        if (variant == GdsResultBannerVariant.Success) {
            Text(
                text = title,
                style =
                    MaterialTheme.typography.headlineMedium.copy(
                        color = MmoColors.White,
                        textAlign = TextAlign.Center,
                    ),
                modifier = Modifier.semantics { heading() },
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CustomInfoCircleIcon(tint = MmoColors.White, modifier = Modifier.size(Spacing.m))
                Text(
                    text = title,
                    style =
                        MaterialTheme.typography.headlineMedium.copy(
                            color = MmoColors.White,
                            textAlign = TextAlign.Center,
                        ),
                    modifier = Modifier.semantics { heading() },
                )
            }
        }
        content?.invoke(this)
    }
}

/**
 * GOV.UK "Warning text" pattern: a filled circle "!" icon beside bold body text, deliberately **not**
 * a coloured banner (per GDS guidance, warning text relies on the icon + bold weight, not background
 * colour) — used for "You must record your catch within 24 hours of landing." on the pending-sync screen.
 */
@Composable
fun GdsWarningText(
    text: String,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().testTag(testTag),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        CustomWarningIcon(tint = MmoColors.Text, modifier = Modifier.size(Spacing.m))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
        )
    }
}

/**
 * GOV.UK "Inset text" pattern: a bordered, indented panel used for the check-your-answers declaration
 * ("By submitting this record, you agree...") and available for reuse anywhere similar emphasis is needed.
 */
@Composable
fun GdsInsetText(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = MmoColors.Grey3)
                .padding(start = Spacing.s, top = Spacing.s, end = Spacing.s, bottom = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        content()
    }
}
