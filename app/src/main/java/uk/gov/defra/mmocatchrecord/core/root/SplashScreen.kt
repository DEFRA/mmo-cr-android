package uk.gov.defra.mmocatchrecord.core.root

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.MmoColors
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.common.design.Spacing

/** Fixed width of the centred wordmark; height follows automatically to preserve its aspect ratio. */
private val SplashWordmarkWidth = 260.dp

/** Fixed width of the bottom-pinned crown; height follows automatically to preserve its aspect ratio. */
private val SplashCrownWidth = 64.dp

/**
 * In-app cold-start splash: a full-bleed GOV.UK-blue background with the GOV.UK wordmark centred on
 * screen and the white Tudor crown pinned near the bottom — the full-size brand lock-up from the design.
 *
 * This is the [RootPhase.SPLASH] destination, shown only for the brief, local-only window while
 * [SessionCoordinator] resolves the real phase (sign-in / app-lock / home) — see [RootNavigation].
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MmoColors.GovBlue)
                .testTag(RootScreenTestTags.SPLASH_SCREEN),
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_govuk_logo),
            contentDescription = stringResource(R.string.gov_uk),
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .systemBarsPadding()
                    .width(SplashWordmarkWidth),
        )
        Image(
            painter = painterResource(id = R.drawable.ic_govuk_crown),
            contentDescription = stringResource(R.string.gov_uk_crown_logo),
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .systemBarsPadding()
                    .padding(bottom = Spacing.xxl)
                    .width(SplashCrownWidth),
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SplashScreenPreview() {
    MmoTheme {
        SplashScreen()
    }
}
