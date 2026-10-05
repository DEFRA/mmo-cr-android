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

    // Selection (statistical sub-rectangle schematic grid — see MapScreen)

    /** Pale tint of [GovBlue] used as a selected grid cell's fill, per the confirmed screenshot. */
    val SelectedTint = Color(0xFFD2E2F1)

    // Offline fisheries map (Canvas-rendered statistical sub-rectangle map — see MapScreen/MapCanvas.kt).
    // Colours per the confirmed iOS screenshot; not GDS palette colours, so kept in their own named group.

    /** Land fill. */
    val MapLand = Color(0xFF0B4143)

    /** Sub-rectangle grid line stroke and (at low alpha) fill, and sub_code label text. */
    val MapGridLine = Color(0xFF0B6B3A)

    /** Selected sub-rectangle fill/stroke and its label "pill" background. */
    val MapSelectedFill = Color(0xFFE8A63A)

    /** Port marker dot fill. */
    val MapPortDot = Color(0xFF01FEE2)
}
