package uk.gov.defra.mmocatchrecord.core.root

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
private val SplashWordmarkWidth = 190.dp

/** Fixed height of the bottom crown, within the GOV.UK brand lock-up's usual 40–56dp range. */
private val SplashCrownHeight = 48.dp

/**
 * In-app cold-start splash: a full-bleed GOV.UK-blue background with the GOV.UK wordmark centred on
 * screen and the white Tudor crown pinned near the bottom — the full-size brand lock-up from the design.
 *
 * This is the [RootPhase.SPLASH] destination, shown only for the brief, local-only window while
 * [SessionCoordinator] resolves the real phase (sign-in / app-lock / home) — see [RootNavigation]. It is
 * rendered as ordinary Compose content rather than relying solely on the Android 12+ system splash
 * screen's animated-icon slot, which is masked to a circle and sized like an app icon: that slot renders
 * the wordmark smaller than this full design. The system splash (see the `Theme.MMOCatchRecord.Starting`
 * styles in `res/values` and `res/values-v31`) still shows the GOV.UK wordmark + crown at process start
 * (so the cold start never shows the bare launcher icon), and this composable takes over immediately
 * after to render the complete, unmasked lock-up — the crown position matches across the handover.
 *
 * The background fills the entire screen edge-to-edge (full bleed, matching the design); only the
 * wordmark and crown content are inset from the status/navigation bars, via [Modifier.systemBarsPadding].
 * There is no artificial delay here — the splash is dismissed the instant [RootPhase] resolves.
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
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_govuk_logo),
                contentDescription = stringResource(R.string.gov_uk),
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .width(SplashWordmarkWidth),
            )
            Image(
                painter = painterResource(id = R.drawable.ic_govuk_crown),
                // Decorative: the wordmark above already conveys the "GOV.UK" identity to TalkBack, so
                // the crown must not be announced again per the app's accessibility instructions.
                contentDescription = null,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = Spacing.xl)
                        .height(SplashCrownHeight),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SplashScreenPreview() {
    MmoTheme {
        SplashScreen()
    }
}
