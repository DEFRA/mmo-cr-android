package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * GDS-style spacing scale (multiples of the 5px/dp GOV.UK grid), for consistent layout across the app.
 *
 * See https://design-system.service.gov.uk/styles/spacing/
 */
object Spacing {
    val none: Dp = 0.dp
    val xxs: Dp = 5.dp
    val xs: Dp = 10.dp
    val s: Dp = 15.dp
    val m: Dp = 20.dp
    val l: Dp = 30.dp
    val xl: Dp = 40.dp
    val xxl: Dp = 50.dp

    /** Minimum touch target size per WCAG 2.2 AA (2.5.8 Target Size, Level AA) and Material guidance. */
    val minTouchTarget: Dp = 48.dp
}
