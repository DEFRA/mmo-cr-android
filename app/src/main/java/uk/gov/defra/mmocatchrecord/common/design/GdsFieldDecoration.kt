@file:Suppress("detekt.FunctionNaming")

package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uk.gov.defra.mmocatchrecord.R

/** Shared GOV.UK text-field box: a 40dp drawn input, 1dp border, inside a 48dp touch target. */
object GdsFieldDefaults {
    val FieldHeight: Dp = 40.dp
    val BorderWidth: Dp = 1.dp

    // Centres a 20sp bodyLarge line in the 40dp box: (40 - 20) / 2.
    private val TextVerticalPadding: Dp = 10.dp

    /** Space above/below the drawn box; with the 40dp box they make up the 48dp touch target. */
    data class Insets(
        val top: Dp = (Spacing.minTouchTarget - FieldHeight) / 2,
        val bottom: Dp = (Spacing.minTouchTarget - FieldHeight) / 2,
    )

    fun contentPadding(insets: Insets = Insets()): PaddingValues =
        PaddingValues(
            start = Spacing.xs,
            end = Spacing.xs,
            top = insets.top + TextVerticalPadding,
            bottom = insets.bottom + TextVerticalPadding,
        )
}

/** The drawn box for a text-field decorator: white with a 1dp border, greyed out when disabled. */
@Composable
fun GdsFieldContainer(
    enabled: Boolean,
    isError: Boolean,
    modifier: Modifier = Modifier,
    insets: GdsFieldDefaults.Insets = GdsFieldDefaults.Insets(),
) {
    val borderColor =
        when {
            !enabled -> MmoColors.Grey1
            isError -> MmoColors.ErrorRed
            else -> MmoColors.Text
        }
    Box(
        modifier
            .padding(top = insets.top, bottom = insets.bottom)
            .background(if (enabled) MmoColors.White else MmoColors.Background)
            .border(GdsFieldDefaults.BorderWidth, borderColor),
    )
}

/** Decoration for any Basic*TextField: the [GdsFieldContainer] behind the text and an optional placeholder. */
@Composable
fun GdsFieldDecorationBox(
    innerTextField: @Composable () -> Unit,
    isEmpty: Boolean,
    enabled: Boolean,
    isError: Boolean,
    placeholder: String? = null,
    insets: GdsFieldDefaults.Insets = GdsFieldDefaults.Insets(),
) {
    Box {
        GdsFieldContainer(enabled, isError, Modifier.matchParentSize(), insets)
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.minTouchTarget)
                    .padding(GdsFieldDefaults.contentPadding(insets)),
        ) {
            if (isEmpty && placeholder != null) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MmoColors.Grey1,
                    // Callers name the field with the placeholder, so it is not announced twice.
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
            innerTextField()
        }
    }
}

/** Text style every GOV.UK field uses; disabled text is greyed so the field does not look editable. */
@Composable
fun gdsFieldTextStyle(enabled: Boolean = true): TextStyle =
    MaterialTheme.typography.bodyLarge.copy(color = if (enabled) MmoColors.Text else MmoColors.Grey1)

val GdsFieldCursorBrush: Brush = SolidColor(MmoColors.Text)

/** [errorText] with the GOV.UK "Error:" prefix, so every field announces errors the same way. */
@Composable
fun gdsErrorAnnouncement(errorText: String?): String? {
    val prefix = stringResource(R.string.gds_error_prefix)
    return errorText?.let { "$prefix $it" }
}
