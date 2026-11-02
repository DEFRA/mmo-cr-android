package uk.gov.defra.mmocatchrecord.feature.signin.domain

import javax.inject.Inject

/**
 * Repository abstraction over authenticating a user against MMO identity. Must call the identity endpoint
 * over TLS only, must never log the supplied credentials, and must fail only with [SignInException].
 */
fun interface SignInRepository {
    suspend fun signIn(
        username: String,
        password: String,
    ): Result<SignInSession>
}

/**
 * Use-case wrapping [SignInRepository.signIn]. Normalises every non-conforming failure to
 * [SignInException], so the presentation layer can only ever observe a mapped [SignInFailure].
 */
class SignInUseCase
    @Inject
    constructor(
        private val repository: SignInRepository,
    ) {
        suspend operator fun invoke(
            username: String,
            password: String,
        ): Result<SignInSession> {
            val result =
                runCatching { repository.signIn(username, password) }
                    .getOrElse { thrown -> Result.failure(thrown) }
            return result.fold(
                onSuccess = { session -> Result.success(session) },
                onFailure = { thrown -> Result.failure(thrown.asSignInException()) },
            )
        }

        private fun Throwable.asSignInException(): SignInException =
            this as? SignInException ?: SignInException(SignInFailure.Unknown(UNCLASSIFIED_FAILURE))

        private companion object {
            const val UNCLASSIFIED_FAILURE = "unclassified sign-in failure"
        }
    }
