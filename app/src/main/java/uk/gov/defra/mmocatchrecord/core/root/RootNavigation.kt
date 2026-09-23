package uk.gov.defra.mmocatchrecord.core.root

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import uk.gov.defra.mmocatchrecord.common.navigation.AppLockRoute
import uk.gov.defra.mmocatchrecord.common.navigation.HomeRoute
import uk.gov.defra.mmocatchrecord.common.navigation.MmoNavHost
import uk.gov.defra.mmocatchrecord.common.navigation.SignInRoute
import uk.gov.defra.mmocatchrecord.common.navigation.SplashRoute

/**
 * Minimum time [RootPhase.SPLASH] (the GOV.UK wordmark + crown, see [SplashScreen]) stays on screen
 * *after* it has actually been painted (see `onFirstFrameRendered`/[MainActivity] below) — not from
 * composition. [SessionCoordinator] derives the real phase from a local, near-instant
 * [uk.gov.defra.mmocatchrecord.core.security.SessionStore] read, often resolving within a single frame of
 * process start, so without this floor the phase can change before the user has had a chance to actually
 * perceive the branding.
 */
private const val SPLASH_MINIMUM_VISIBLE_DURATION_MILLIS = 600L

/**
 * Root cause of the wordmark/crown never appearing on some cold starts (confirmed by instrumented
 * testing — `dumpsys window windows` showed the real Activity window with `mDrawState=NO_SURFACE` and
 * `mViewVisibility=INVISIBLE` for several hundred ms to over a second after launch, on a cold, JIT-cold
 * Hilt + Compose start): a naive minimum-visible-duration floor timed from Compose *composition* (which
 * happens well before the window has a real surface to draw to) elapses while the window still isn't
 * actually painting anything. By the time the window finally produces its first real frame,
 * [SessionCoordinator] has *already* resolved past [RootPhase.SPLASH] and the floor has *already*
 * elapsed, so the very first thing the user ever sees is the resolved phase (sign-in/app-lock/home) —
 * the branded splash was composed but never actually visible.
 *
 * Two coordinated fixes address this (see [MainActivity] for the first):
 *  1. [MainActivity] keeps the *system* splash on screen (`setKeepOnScreenCondition`) until Compose has
 *     rendered a real first frame (`onFirstFrameRendered` below), guaranteeing the in-app [SplashScreen]
 *     — not the resolved phase — is the first thing ever painted.
 *  2. [SPLASH_MINIMUM_VISIBLE_DURATION_MILLIS] is measured from that same first-frame moment (captured in
 *     `firstFrameRenderedAtElapsedRealtime` below via `View.doOnPreDraw`), not from composition, so the
 *     floor can no longer silently elapse before anything is actually visible.
 *
 * [MmoNavHost]'s `startDestination` is also unconditionally [SplashRoute] (never derived from the
 * collected ui state) — `SessionCoordinator`'s background phase-resolution coroutine can otherwise beat
 * `RootNavigation`'s first `collectAsStateWithLifecycle()` snapshot, making the collected phase already
 * the *resolved* phase on this composable's very first composition, so a state-derived start destination
 * could skip [SplashRoute] (and therefore [SplashScreen]) entirely rather than just showing it too briefly.
 */
private fun RootPhase.toRoute(): Any =
    when (this) {
        RootPhase.SPLASH -> SplashRoute
        RootPhase.SIGN_IN -> SignInRoute
        RootPhase.APP_LOCK -> AppLockRoute
        RootPhase.HOME -> HomeRoute
    }

/**
 * Root composable: observes [SessionCoordinator]'s derived [RootPhase] and keeps the nav graph's current
 * destination in sync with it, hosting the Stage-1 placeholder screens via [MmoNavHost].
 *
 * Also re-evaluates the re-entry decision whenever the app returns to the foreground
 * ([Lifecycle.Event.ON_RESUME]) — otherwise an elapsed biometric re-entry timeout would only ever be
 * enforced once, at [SessionCoordinator] construction, and a resumed app would stay on HOME forever.
 *
 * @param onFirstFrameRendered invoked exactly once, the first time this composable's content has actually
 * been drawn (see `firstFrameRenderedAtElapsedRealtime` below) — [MainActivity] uses this to release the
 * system splash it was holding on screen via `setKeepOnScreenCondition` until real content exists to show.
 */
@Composable
fun RootNavigation(
    sessionCoordinator: SessionCoordinator,
    modifier: Modifier = Modifier,
    onFirstFrameRendered: () -> Unit = {},
) {
    val uiState by sessionCoordinator.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current

    // Only set once, the first time this View is actually about to draw a frame — see the root-cause
    // note above for why this (not composition time) is the correct anchor for the splash floor.
    var firstFrameRenderedAtElapsedRealtime by remember { mutableStateOf<Long?>(null) }

    DisposableEffect(view) {
        val preDrawListener =
            view.doOnPreDraw {
                firstFrameRenderedAtElapsedRealtime = SystemClock.elapsedRealtime()
                onFirstFrameRendered()
            }
        onDispose { preDrawListener.removeListener() }
    }

    DisposableEffect(lifecycleOwner, sessionCoordinator) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    sessionCoordinator.dispatch(RootEvent.AppResumed)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.phase, firstFrameRenderedAtElapsedRealtime) {
        val shownAtElapsedRealtime = firstFrameRenderedAtElapsedRealtime ?: return@LaunchedEffect
        val destination = uiState.phase.toRoute()
        // Guard against the redundant self-navigation that would otherwise fire on the very first
        // collection (uiState.phase == SPLASH, destination == the already-current start destination).
        if (destination == SplashRoute) return@LaunchedEffect
        // Floor the splash -> resolved-phase handover only, from the real first-frame timestamp; later
        // phase changes (e.g. sign-out, app resume, biometric re-entry) navigate immediately as before.
        val elapsedSinceSplashShown = SystemClock.elapsedRealtime() - shownAtElapsedRealtime
        val remainingSplashMillis = SPLASH_MINIMUM_VISIBLE_DURATION_MILLIS - elapsedSinceSplashShown
        if (remainingSplashMillis > 0) {
            delay(remainingSplashMillis)
        }
        navController.navigate(destination) {
            // popUpTo(0) is a fragile idiom: id 0 never matches a real (hash-based) destination id, so
            // NavController silently pops nothing — every phase change (now SPLASH, SIGN_IN, APP_LOCK,
            // HOME) just pushes a new entry, and the back stack grows unbounded across the app's
            // lifetime. popUpTo(navController.graph.id) is the documented way to clear the entire back
            // stack: the graph's own id is always valid, so this reliably removes every prior phase
            // destination (including SPLASH, which must never be reachable via back navigation once the
            // real phase resolves).
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    MmoNavHost(
        navController = navController,
        // Deliberately NOT uiState.phase.toRoute() — see the root-cause note above. Hardcoding SplashRoute
        // guarantees SplashScreen (the GOV.UK wordmark + crown) is always composed first, immune to how
        // fast SessionCoordinator resolves the real phase.
        startDestination = SplashRoute,
        onRootEvent = sessionCoordinator::dispatch,
        modifier = modifier,
    )
}
