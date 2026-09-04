package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.ui.graphics.Color

/**
 * GOV.UK Design System colour palette (light-only), used to build the [MmoTheme] colour scheme.
 *
 * See https://design-system.service.gov.uk/styles/colour/ — colours are copied verbatim so that
 * contrast ratios documented by GDS continue to apply.
 */
object MmoColors {
    // Brand / primary
    val GovBlue = Color(0xFF1D70B8)
    val GovBlueHover = Color(0xFF003078)

    // Text & background
    val Text = Color(0xFF0B0C0C)
    val White = Color(0xFFFFFFFF)
    val Background = Color(0xFFF3F2F1)

    // Feedback
    val ErrorRed = Color(0xFFD4351C)
    val SuccessGreen = Color(0xFF00703C)
    val FocusYellow = Color(0xFFFFDD00)

    // Links
    val Link = Color(0xFF1D70B8)
    val LinkVisited = Color(0xFF4C2C92)
    val LinkHover = Color(0xFF003078)

    // Greyscale
    val Grey1 = Color(0xFF6F777B)
    val Grey2 = Color(0xFFB1B4B6)
    val Grey3 = Color(0xFFD8DDE0)
    val Grey4 = Color(0xFFF3F2F1)
}
