package uk.gov.defra.mmocatchrecord.core.root

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.common.design.Spacing

/**
 * Compose test tags for instrumented/UI tests. Kept in one place so tests don't hard-code strings.
 *
 * [APP_LOCK_SCREEN]/[APP_LOCK_ACTION] back a real, wired-in composable ([AppLockScreen]) and
 * [SPLASH_SCREEN] backs [SplashScreen] — the real sign-in and home screens now live in
 * `feature.signin.presentation.SignInScreen`
 * ([uk.gov.defra.mmocatchrecord.feature.signin.presentation.SignInScreenTestTags]) and
 * `feature.home.presentation.HomeScreen` respectively; their Stage-1 placeholder equivalents (and
 * matching test tags) were removed from here once superseded, so nothing in this object refers to
 * dead composables.
 */
object RootScreenTestTags {
    const val APP_LOCK_SCREEN = "app_lock_screen"

    // Stable, per-screen tags for the primary action button. Tests must drive/assert elements by tag
    // per testing.instructions ("stable testTags/semantics, not display text alone") — several
    // placeholder screens share the same action label text (e.g. "Sign in"), so text alone is ambiguous.
    const val APP_LOCK_ACTION = "app_lock_screen_action"

    /** Root container of [SplashScreen], the initial [RootPhase.SPLASH] destination. */
    const val SPLASH_SCREEN = "splash_screen"
}

/** Stage-1 placeholder app-lock (biometric re-entry) screen. */
@Composable
fun AppLockScreen(
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        testTag = RootScreenTestTags.APP_LOCK_SCREEN,
        actionTestTag = RootScreenTestTags.APP_LOCK_ACTION,
        title = "App locked",
        body = "Unlock the app to continue.",
        actionLabel = "Unlock",
        onAction = onUnlock,
        modifier = modifier,
    )
}

@Composable
private fun PlaceholderScreen(
    testTag: String,
    actionTestTag: String,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize().testTag(testTag)) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                modifier =
                    Modifier.semantics {
                        heading()
                    },
            )
            Text(text = body, style = MaterialTheme.typography.bodyLarge)
            Button(
                onClick = onAction,
                modifier =
                    Modifier
                        .testTag(actionTestTag)
                        .semantics { role = Role.Button }
                        .heightIn(min = Spacing.minTouchTarget)
                        .widthIn(min = Spacing.minTouchTarget),
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AppLockScreenPreview() {
    MmoTheme {
        AppLockScreen(onUnlock = {})
    }
}
