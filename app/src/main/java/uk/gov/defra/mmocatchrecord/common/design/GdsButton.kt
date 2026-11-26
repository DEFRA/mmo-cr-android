@file:Suppress("detekt.FunctionNaming")

package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Colours for one [GdsButtonFrame] state set; a null [border] or [edge] is not drawn. */
@Immutable
internal data class GdsButtonColors(
    val fill: Color,
    val pressedFill: Color,
    val content: Color,
    val edge: Color? = null,
    val border: Color? = null,
)

/** GOV.UK button: a 40dp drawn box inside a 48dp touch target, turning focus-yellow with black edges on focus. */
@Composable
@Suppress("LongParameterList")
internal fun GdsButtonFrame(
    onClick: () -> Unit,
    colors: GdsButtonColors,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    edgeHeight: Dp = 2.dp,
    horizontalPadding: Dp = Spacing.m,
    content: @Composable (contentColor: Color) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val fill =
        when {
            focused -> MmoColors.FocusYellow
            pressed -> colors.pressedFill
            else -> colors.fill
        }
    val edge = if (focused) MmoColors.Text else colors.edge
    val border = if (focused && colors.border != null) MmoColors.Text else colors.border
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .heightIn(min = Spacing.minTouchTarget)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ).padding(vertical = (Spacing.minTouchTarget - GdsFieldDefaults.FieldHeight) / 2)
                .background(fill)
                .then(if (border != null) Modifier.border(1.dp, border) else Modifier)
                .drawBehind {
                    if (edge != null) {
                        val h = edgeHeight.toPx()
                        drawRect(color = edge, topLeft = Offset(0f, size.height - h), size = size.copy(height = h))
                    }
                }.padding(horizontal = horizontalPadding, vertical = BUTTON_TEXT_PADDING),
    ) {
        content(if (focused) MmoColors.Text else colors.content)
    }
}

// bodyLarge's 20sp line plus 10dp either side fills the 40dp box at default font scale.
private val BUTTON_TEXT_PADDING = 10.dp
