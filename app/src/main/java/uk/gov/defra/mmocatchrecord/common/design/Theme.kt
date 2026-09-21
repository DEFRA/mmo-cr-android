package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Light-only Material 3 colour scheme mapped from [MmoColors].
 *
 * GOV.UK services do not support dark mode or per-device dynamic colour theming — colours are fixed and
 * WCAG-AA contrast pairs (text/background) are preserved from the GOV.UK Design System, so there is
 * deliberately **no** `darkColorScheme` and **no** `dynamicColorScheme` here.
 */
private val MmoLightColorScheme =
    lightColorScheme(
        primary = MmoColors.GovBlue,
        onPrimary = MmoColors.White,
        secondary = MmoColors.LinkVisited,
        onSecondary = MmoColors.White,
        error = MmoColors.ErrorRed,
        onError = MmoColors.White,
        background = MmoColors.White,
        onBackground = MmoColors.Text,
        surface = MmoColors.White,
        onSurface = MmoColors.Text,
        surfaceVariant = MmoColors.Grey4,
        onSurfaceVariant = MmoColors.Text,
        outline = MmoColors.Grey1,
    )

/**
 * App-wide theme wrapper. Light-only, no dynamic colour, per GOV.UK service design conventions.
 */
@Composable
fun MmoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MmoLightColorScheme,
        typography = MmoTypography,
        content = content,
    )
}

/**
 * GOV.UK-style visible focus indicator: a 3dp solid `#ffdd00` (focus yellow) box with a contrasting
 * black "shadow" edge, matching https://design-system.service.gov.uk/get-started/focus-states/.
 *
 * Apply to any focusable/interactive composable so keyboard/switch-access focus is always visible
 * (WCAG 2.2 AA 2.4.11 Focus Not Obscured / 2.4.7 Focus Visible) — never rely on colour alone elsewhere,
 * but this indicator itself is a shape+colour combination as GDS specifies.
 */
fun Modifier.govukFocusIndicator(): Modifier =
    this
        .border(width = 1.dp, color = Color.Black)
        .border(width = 3.dp, color = MmoColors.FocusYellow)
