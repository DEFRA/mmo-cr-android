package uk.gov.defra.mmocatchrecord.feature.signin.domain

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

    /**
     * Unclassified failure. [message] is a **mapped, safe** diagnostic (never an SDK/HTTP exception
     * message) and is never displayed; user-facing copy is always resolved from string resources.
     */
    data class Unknown(
        val message: String,
    ) : SignInFailure()
}

/**
 * The only throwable a [SignInRepository] may fail with, so [SignInFailure] survives the `Result` channel
 * intact. Carries no message of its own, so nothing unsafe can leak through a stack trace or log.
 */
class SignInException(
    val failure: SignInFailure,
) : Exception()
