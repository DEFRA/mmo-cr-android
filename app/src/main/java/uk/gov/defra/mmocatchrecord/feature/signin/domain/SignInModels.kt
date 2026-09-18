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
    data object InvalidCredentials : SignInFailure()

    data object Network : SignInFailure()

    data class Unknown(
        val message: String,
    ) : SignInFailure()
}
