package uk.gov.defra.mmocatchrecord.core.root

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import uk.gov.defra.mmocatchrecord.core.architecture.BaseViewModel
import uk.gov.defra.mmocatchrecord.core.architecture.ViewState
import uk.gov.defra.mmocatchrecord.core.security.BiometricPreferenceStore
import uk.gov.defra.mmocatchrecord.core.security.BiometricReentryPolicy
import uk.gov.defra.mmocatchrecord.core.security.BiometricRepository
import uk.gov.defra.mmocatchrecord.core.security.ReentryDecision
import uk.gov.defra.mmocatchrecord.core.security.SessionStore
import javax.inject.Inject

/** Root navigation state — currently just the derived [RootPhase]. */
data class RootUiState(
    val phase: RootPhase,
) : ViewState

/** UDF events the root graph can dispatch back to [SessionCoordinator]. */
sealed interface RootEvent {
    /** The user completed sign-in successfully. */
    data object SignedIn : RootEvent

    /**
     * The user tapped "Unlock" on the app-lock screen. This does **not** unlock the app by itself —
     * [SessionCoordinator] must run [BiometricRepository.authenticate] and only record a new
     * authentication (and therefore leave [RootPhase.APP_LOCK]) if that call succeeds.
     */
    data object BiometricUnlockRequested : RootEvent

    /** The user signed out (or session was invalidated). */
    data object SignedOut : RootEvent

    /**
     * The app returned to the foreground. Re-evaluates [RootPhase] so an elapsed re-entry timeout is
     * enforced immediately on resume, not only when [SessionCoordinator] is first constructed.
     */
    data object AppResumed : RootEvent
}

/**
 * Derives [RootPhase] from [SessionStore] + [BiometricPreferenceStore] + [BiometricReentryPolicy], and
 * is the unit-test target for the app's sign-in / app-lock / home routing decision.
 */
@HiltViewModel
class SessionCoordinator
    @Inject
    constructor(
        private val sessionStore: SessionStore,
        private val biometricPreferenceStore: BiometricPreferenceStore,
        private val reentryPolicy: BiometricReentryPolicy,
        private val biometricRepository: BiometricRepository,
        private val clock: () -> Long = System::currentTimeMillis,
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
    ) : BaseViewModel<RootUiState, RootEvent>(
            initialState = RootUiState(phase = RootPhase.SIGN_IN),
            defaultDispatcher = dispatcher,
        ) {
        init {
            refreshPhase()
        }

        override fun dispatch(event: RootEvent) {
            when (event) {
                RootEvent.SignedIn ->
                    launchInViewModelScope {
                        sessionStore.recordAuthenticatedNow(clock())
                        refreshPhaseNow()
                    }

                RootEvent.BiometricUnlockRequested ->
                    launchInViewModelScope {
                        // Only treat the app as re-entered if the biometric prompt actually succeeds.
                        // A cancelled/failed attempt must leave the state (and therefore RootPhase.APP_LOCK)
                        // unchanged — never optimistically unlock on user intent alone.
                        if (biometricRepository.authenticate().isSuccess) {
                            sessionStore.recordAuthenticatedNow(clock())
                        }
                        refreshPhaseNow()
                    }

                RootEvent.SignedOut ->
                    launchInViewModelScope {
                        sessionStore.clear()
                        updateState { it.copy(phase = RootPhase.SIGN_IN) }
                    }

                RootEvent.AppResumed ->
                    launchInViewModelScope { refreshPhaseNow() }
            }
        }

        private fun refreshPhase() {
            launchInViewModelScope { refreshPhaseNow() }
        }

        private suspend fun refreshPhaseNow() {
            val lastAuthenticatedAtMillis = sessionStore.getLastAuthenticatedAtMillis()
            val phase =
                if (lastAuthenticatedAtMillis == null) {
                    RootPhase.SIGN_IN
                } else {
                    val biometricEnabled = biometricPreferenceStore.isBiometricReentryEnabled()
                    when (
                        reentryPolicy.evaluate(
                            isBiometricEnabled = biometricEnabled,
                            lastAuthenticatedAtMillis = lastAuthenticatedAtMillis,
                            nowMillis = clock(),
                        )
                    ) {
                        ReentryDecision.APP_LOCK -> RootPhase.APP_LOCK
                        ReentryDecision.PASS_THROUGH -> RootPhase.HOME
                    }
                }
            updateState { it.copy(phase = phase) }
        }
    }
