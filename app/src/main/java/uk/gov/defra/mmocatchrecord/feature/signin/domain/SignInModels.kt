package uk.gov.defra.mmocatchrecord.feature.signin.domain

import kotlinx.coroutines.flow.StateFlow

/**
 * Domain model for an authenticated sign-in session. Contains no Android framework types so it stays
 * trivially unit-testable and reusable across data/presentation layers.
 */
data class SignInSession(
    val userId: String,
    val signedInAtEpochMillis: Long,
)

/** Terminal, user-facing sign-in failure reasons. Never carries raw exception messages or PII. */
sealed class SignInFailure {
    /**
     * Returned for an unknown account *and* a wrong password alike: distinguishing them would turn the
     * sign-in screen into an account-enumeration oracle (ADR 0019).
     */
    data object InvalidCredentials : SignInFailure()

    /** Transient connectivity/transport failure — retryable. */
    data object Network : SignInFailure()

    /** The account requires a verification step (e.g. a one-time code) this build does not render yet. */
    data object VerificationNotSupported : SignInFailure()

    /** The user backed out of the browser web-fallback leg (ADR 0019 Phase 2.1c) — not an error. */
    data object Cancelled : SignInFailure()

    /**
     * Unclassified failure. [message] is a **mapped, safe** diagnostic (never an SDK/HTTP exception
     * message) and is never displayed; user-facing copy is always resolved from string resources.
     */
    data class Unknown(
        val message: String,
    ) : SignInFailure()
}

/** Mid-sign-in stage, observed by the ViewModel to render an accessible browser hand-off announcement. */
enum class SignInStage {
    Idle,
    RedirectingToBrowser,
}

/** Exposes [SignInStage] so the ViewModel can react to the hand-off without depending on MSAL/Android types. */
interface SignInStageObserver {
    val stage: StateFlow<SignInStage>

    /** The UI confirms the hand-off notice has been composed (WCAG 2.2 SC 4.1.3); default no-op for fakes. */
    fun acknowledgeBrowserNoticeShown() {
        // Intentionally empty: only an observer that actually redirects needs to react to this.
    }
}

/**
 * The only throwable a [SignInRepository] may fail with, so [SignInFailure] survives the `Result` channel
 * intact. Carries no message of its own, so nothing unsafe can leak through a stack trace or log.
 */
class SignInException(
    val failure: SignInFailure,
) : Exception()
