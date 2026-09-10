package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Custom PlayArrow / Triangle shape drawer */
@Composable
fun CustomPlayArrowIcon(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val path =
            Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, size.height / 2f)
                lineTo(0f, size.height)
                close()
            }
        drawPath(path = path, color = tint)
    }
}

/** Custom ArrowForward shape drawer */
@Composable
fun CustomArrowForwardIcon(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawLine(color = tint, start = Offset(0f, h / 2f), end = Offset(w, h / 2f), strokeWidth = 2.dp.toPx())
        drawLine(
            color = tint,
            start = Offset(w - 6.dp.toPx(), h / 2f - 4.dp.toPx()),
            end = Offset(w, h / 2f),
            strokeWidth = 2.dp.toPx(),
        )
        drawLine(
            color = tint,
            start = Offset(w - 6.dp.toPx(), h / 2f + 4.dp.toPx()),
            end = Offset(w, h / 2f),
            strokeWidth = 2.dp.toPx(),
        )
    }
}

/** Custom Home icon shape drawer */
@Composable
fun CustomHomeIcon(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path =
            Path().apply {
                moveTo(w / 2f, h * 0.15f)
                lineTo(w * 0.15f, h * 0.5f)
                lineTo(w * 0.25f, h * 0.5f)
                lineTo(w * 0.25f, h * 0.85f)
                lineTo(w * 0.75f, h * 0.85f)
                lineTo(w * 0.75f, h * 0.5f)
                lineTo(w * 0.85f, h * 0.5f)
                close()
            }
        drawPath(path = path, color = tint)
    }
}

/** Custom Notifications bell shape drawer */
@Composable
fun CustomNotificationsIcon(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path =
            Path().apply {
                moveTo(w / 2f, h * 0.2f)
                quadraticTo(w * 0.3f, h * 0.2f, w * 0.3f, h * 0.6f)
                lineTo(w * 0.2f, h * 0.75f)
                lineTo(w * 0.8f, h * 0.75f)
                lineTo(w * 0.7f, h * 0.6f)
                quadraticTo(w * 0.7f, h * 0.2f, w / 2f, h * 0.2f)
                close()
            }
        drawPath(path = path, color = tint)
        drawCircle(color = tint, radius = w * 0.08f, center = Offset(w / 2f, h * 0.82f))
    }
}

/** Custom Settings gear shape drawer */
@Composable
fun CustomSettingsIcon(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val outerR = w * 0.30f

        drawCircle(color = tint, radius = outerR, style = Stroke(width = w * 0.12f))
        drawCircle(color = tint, radius = w * 0.1f)
        for (i in 0 until 8) {
            val angle = i * Math.PI / 4
            val cos = Math.cos(angle).toFloat()
            val sin = Math.sin(angle).toFloat()
            drawLine(
                color = tint,
                start = Offset(cx + outerR * cos, cy + outerR * sin),
                end = Offset(cx + (outerR + 3.dp.toPx()) * cos, cy + (outerR + 3.dp.toPx()) * sin),
                strokeWidth = w * 0.12f,
            )
        }
    }
}

/** GOV.UK crown vector icon */
@Composable
fun GdsCrownIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(36.dp)) {
        val w = size.width
        val h = size.height

        drawRect(
            color = Color.White,
            topLeft = Offset(w * 0.15f, h * 0.75f),
            size = Size(w * 0.7f, h * 0.1f),
        )

        val crownPath =
            Path().apply {
                moveTo(w * 0.15f, h * 0.75f)
                lineTo(w * 0.1f, h * 0.4f)
                lineTo(w * 0.3f, h * 0.55f)
                lineTo(w * 0.5f, h * 0.2f)
                lineTo(w * 0.7f, h * 0.55f)
                lineTo(w * 0.9f, h * 0.4f)
                lineTo(w * 0.85f, h * 0.75f)
                close()
            }
        drawPath(path = crownPath, color = Color.White)

        drawCircle(
            color = MmoColors.GovBlue,
            radius = w * 0.03f,
            center = Offset(w * 0.3f, h * 0.8f),
        )
        drawCircle(
            color = MmoColors.GovBlue,
            radius = w * 0.03f,
            center = Offset(w * 0.5f, h * 0.8f),
        )
        drawCircle(
            color = MmoColors.GovBlue,
            radius = w * 0.03f,
            center = Offset(w * 0.7f, h * 0.8f),
        )

        drawCircle(
            color = Color.White,
            radius = w * 0.04f,
            center = Offset(w * 0.1f, h * 0.36f),
        )
        drawCircle(
            color = Color.White,
            radius = w * 0.04f,
            center = Offset(w * 0.5f, h * 0.16f),
        )
        drawCircle(
            color = Color.White,
            radius = w * 0.04f,
            center = Offset(w * 0.9f, h * 0.36f),
        )
    }
}

/** Royal Crest Placeholder icon since official license artwork is restricted */
@Composable
fun RoyalCrestPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .size(100.dp)
                .padding(Spacing.s),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            drawCircle(
                color = MmoColors.Grey2,
                radius = w * 0.4f,
                style = Stroke(width = 2.dp.toPx()),
            )
            drawPath(
                path =
                    Path().apply {
                        moveTo(w * 0.1f, h * 0.8f)
                        lineTo(w * 0.2f, h * 0.3f)
                        lineTo(w * 0.35f, h * 0.6f)
                    },
                color = MmoColors.Grey2,
                style = Stroke(width = 2.dp.toPx()),
            )
            drawPath(
                path =
                    Path().apply {
                        moveTo(w * 0.9f, h * 0.8f)
                        lineTo(w * 0.8f, h * 0.3f)
                        lineTo(w * 0.65f, h * 0.6f)
                    },
                color = MmoColors.Grey2,
                style = Stroke(width = 2.dp.toPx()),
            )
            drawRect(
                color = MmoColors.Grey3,
                topLeft = Offset(w * 0.38f, h * 0.35f),
                size = Size(w * 0.24f, h * 0.35f),
            )
        }
    }
}
