package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.map

import java.util.Locale
import kotlin.math.abs

/**
 * Pure (no Android/Compose dependency) formatting rules for displaying a statistical sub-area's centre
 * coordinate (FR5/FR6) — two decimal places is roughly 1km resolution, appropriate for a 30'x1' rectangle
 * centre. Cardinal (N/S/E/W) rather than signed values, per GOV.UK content guidance (ADR 0017).
 */
object MapCoordinateFormat {
    private const val DECIMAL_PLACES = 2

    /** [value]'s absolute magnitude formatted to [DECIMAL_PLACES], using a fixed `.` decimal separator. */
    fun magnitude(value: Double): String = String.format(Locale.UK, "%.${DECIMAL_PLACES}f", abs(value))

    /** Whether [latitude] reads as "North" (non-negative, per WGS84 convention). */
    fun isNorth(latitude: Double): Boolean = latitude >= 0.0

    /** Whether [longitude] reads as "East" (non-negative, per WGS84 convention). */
    fun isEast(longitude: Double): Boolean = longitude >= 0.0
}
