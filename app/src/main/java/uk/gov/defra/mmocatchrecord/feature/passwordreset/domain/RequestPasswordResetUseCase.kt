package uk.gov.defra.mmocatchrecord.feature.passwordreset.domain

import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/** Outcome of [RequestPasswordResetUseCase]; [Rejected] means the repository was never called. */
sealed interface RequestResetOutcome {
    data class Rejected(
        val error: EmailAddressError,
    ) : RequestResetOutcome

    data class Attempted(
        val result: StartResetResult,
    ) : RequestResetOutcome
}

/** Validates the email, then starts the reset; a thrown repository error becomes a safe [StartResetResult.Failed]. */
class RequestPasswordResetUseCase
    @Inject
    constructor(
        private val repository: PasswordResetRepository,
        private val emailValidator: EmailAddressValidator,
    ) {
        @Suppress("TooGenericExceptionCaught")
        suspend operator fun invoke(email: String): RequestResetOutcome {
            emailValidator.validate(email)?.let { return RequestResetOutcome.Rejected(it) }
            val result =
                try {
                    repository.start(email.trim())
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    StartResetResult.Failed(PasswordResetFailure.Unknown(UNEXPECTED_FAILURE))
                }
            return RequestResetOutcome.Attempted(result)
        }

        private companion object {
            const val UNEXPECTED_FAILURE = "password reset start threw an unexpected exception"
        }
    }
