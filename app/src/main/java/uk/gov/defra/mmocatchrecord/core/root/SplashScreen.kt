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
 * [SessionCoordinator] resolves the real phase (sign-in / app-lock / home) — see [RootNavigation]. The
 * Android 12+ system splash screen (`Theme.MMOCatchRecord.Starting` in `res/values/themes.xml`) shows only
 * the plain GOV.UK-blue background with no logo — it deliberately does not render the wordmark or crown at
 * all, because that slot is masked to a circle and sized like an app icon, so anything drawn there would
 * necessarily be smaller/cropped compared to this full-size, unmasked lock-up, producing a visible
 * size-mismatch "zoom" during the handover between the two. This composable is therefore the *only* place
 * the wordmark + crown are drawn, at their correct full size, with full layout control (no masking).
 * The background fills the entire screen edge-to-edge (full bleed, matching the design); only the
 * wordmark and crown content are inset from the status/navigation bars, via [Modifier.systemBarsPadding].
 * This composable itself renders as soon as it is composed, with no delay; [RootNavigation] is
 * responsible for holding the handover to the next resolved [RootPhase] open for a short minimum
 * duration (see `SPLASH_MINIMUM_VISIBLE_DURATION_MILLIS`) so the wordmark + crown are guaranteed at
 * least one perceivable frame even when [SessionCoordinator] resolves the real phase almost instantly.
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
