@file:Suppress("detekt.FunctionNaming")

package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration

/**
 * A GOV.UK link-styled clickable text action (underlined, [MmoColors.Link]/[MmoColors.Grey1] when
 * disabled), used for the "Remove gear"/"Add another gear"-style secondary actions across the wizard.
 *
 * Rendered as always visible but disabled (dimmed, unclickable — communicated to TalkBack via
 * [Modifier.clickable]'s own `enabled` semantics) rather than hidden outright when [enabled] is false, so
 * the action stays discoverable (a screen-reader/keyboard user can find out it exists and why it's
 * currently unavailable) — see the Phase 3 "Remove gear" no-checked-items edge case note in the change
 * summary. Extracted from `GearSummaryScreen`'s original `RemoveGearLink` so the same pattern is not
 * duplicated across the gear-summary, gear-species-checklist, and progressive-disclosure weight fields.
 *
 * [accessibleLabel] (Phase 8) overrides the announced TalkBack text when the same visible "Change" text is
 * repeated many times on one screen (the check-your-answers "Change" links) — GDS guidance is that each
 * repeated link must announce which row it changes (e.g. "Change vessel"), not identical "Change, Change,
 * Change..." — while the visible [text] itself stays the short, repeated "Change" per the design.
 */
@Composable
fun GdsLinkAction(
    text: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accessibleLabel: String? = null,
) {
    Text(
        text = text,
        color = if (enabled) MmoColors.Link else MmoColors.Grey1,
        style = MaterialTheme.typography.bodyLarge.copy(textDecoration = TextDecoration.Underline),
        modifier =
            modifier
                .heightIn(min = Spacing.minTouchTarget)
                .wrapContentHeight()
                .clickable(enabled = enabled, onClick = onClick, role = Role.Button)
                .testTag(testTag)
                .let {
                    if (accessibleLabel != null) it.semantics { contentDescription = accessibleLabel } else it
                },
    )
}
