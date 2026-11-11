package uk.gov.defra.mmocatchrecord.feature.signin.domain

import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

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
        @Suppress("TooGenericExceptionCaught")
        suspend operator fun invoke(
            username: String,
            password: String,
        ): Result<SignInSession> {
            val result =
                try {
                    repository.signIn(username, password)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (thrown: Exception) {
                    Result.failure(thrown)
                }
            return result.fold(
                onSuccess = { session -> Result.success(session) },
                onFailure = { thrown ->
                    // Cancellation is structured concurrency unwinding, not a sign-in failure to report.
                    if (thrown is CancellationException) {
                        throw thrown
                    }
                    Result.failure(thrown.asSignInException())
                },
            )
        }

        private fun Throwable.asSignInException(): SignInException =
            this as? SignInException ?: SignInException(SignInFailure.Unknown(UNCLASSIFIED_FAILURE))

        private companion object {
            const val UNCLASSIFIED_FAILURE = "unclassified sign-in failure"
        }
    }
