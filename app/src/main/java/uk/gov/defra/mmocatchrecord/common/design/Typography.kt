package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Single font swap-point for the app.
 *
 * GDS Transport (the GOV.UK Design System typeface) is licensed for gov.uk domains only and must NOT be
 * bundled in this non-gov.uk-hosted native app. We fall back to the platform system font
 * ([FontFamily.Default]) and keep GDS-style type scale/weights so the visual rhythm still matches GOV.UK
 * guidance. If a licensed alternative becomes available, swap it in here only.
 */
val MmoFontFamily: FontFamily = FontFamily.Default

/**
 * Material 3 [Typography] built on a GDS-style type scale (sp sizes/weights per
 * https://design-system.service.gov.uk/styles/type-scale/), using [MmoFontFamily].
 */
val MmoTypography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = MmoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 48.sp,
                lineHeight = 56.sp,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = MmoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                lineHeight = 40.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = MmoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 27.sp,
                lineHeight = 34.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = MmoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                lineHeight = 30.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = MmoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                lineHeight = 25.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = MmoFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 19.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.15.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = MmoFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.15.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = MmoFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                letterSpacing = 0.1.sp,
            ),
    )
