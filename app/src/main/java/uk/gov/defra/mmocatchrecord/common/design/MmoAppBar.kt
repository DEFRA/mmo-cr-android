package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.gov.defra.mmocatchrecord.R

/** GOV.UK Header Top App Bar */
@Composable
fun GdsTopAppBar(
    currentLanguage: String,
    onLanguageToggle: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MmoColors.GovBlue,
        contentColor = MmoColors.White,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .statusBarsPadding()
                    .padding(horizontal = Spacing.s, vertical = Spacing.xs)
                    .heightIn(min = Spacing.minTouchTarget),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            GdsAppBarBackButton(onBackClick)
            GdsAppBarTitle()
            GdsAppBarLangToggle(currentLanguage, onLanguageToggle)
        }
    }
}

@Composable
private fun GdsAppBarBackButton(onBackClick: () -> Unit) {
    Row(
        modifier =
            Modifier
                .clickable(onClick = onBackClick)
                .heightIn(min = Spacing.minTouchTarget)
                .padding(end = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.size(12.dp)) {
            val path =
                Path().apply {
                    moveTo(size.width, 0f)
                    lineTo(0f, size.height / 2f)
                    lineTo(size.width, size.height)
                }
            drawPath(path = path, color = Color.White, style = Stroke(width = 2.dp.toPx()))
        }
        Spacer(modifier = Modifier.width(Spacing.xxs))
        Text(
            text = stringResource(R.string.back),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun GdsAppBarTitle() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.heightIn(min = Spacing.minTouchTarget),
    ) {
        GdsCrownIcon()
        Spacer(modifier = Modifier.width(Spacing.xxs))
        Text(
            text = "GOV.UK",
            style =
                MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = (-0.5).sp,
                ),
        )
    }
}

@Composable
private fun GdsAppBarLangToggle(
    currentLanguage: String,
    onLanguageToggle: () -> Unit,
) {
    Button(
        onClick = onLanguageToggle,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White,
            ),
        contentPadding = PaddingValues(0.dp),
        modifier =
            Modifier
                .widthIn(min = Spacing.minTouchTarget)
                .heightIn(min = Spacing.minTouchTarget),
    ) {
        Text(
            text = if (currentLanguage == "en") stringResource(R.string.cym) else stringResource(R.string.eng),
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                ),
        )
    }
}
