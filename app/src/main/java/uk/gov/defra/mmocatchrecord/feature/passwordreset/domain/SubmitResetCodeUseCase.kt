package uk.gov.defra.mmocatchrecord.feature.passwordreset.domain

import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/** Outcome of [SubmitResetCodeUseCase]; [Rejected] means the repository was never called. */
sealed interface SubmitCodeOutcome {
    data class Rejected(
        val error: ResetCodeError,
    ) : SubmitCodeOutcome

    data class Attempted(
        val result: SubmitCodeResult,
    ) : SubmitCodeOutcome
}

/** Normalises and validates the code, then submits it; a thrown repository error becomes a safe failure. */
class SubmitResetCodeUseCase
    @Inject
    constructor(
        private val repository: PasswordResetRepository,
        private val codeValidator: ResetCodeValidator,
    ) {
        @Suppress("TooGenericExceptionCaught")
        suspend operator fun invoke(
            token: PasswordResetToken,
            code: String,
        ): SubmitCodeOutcome {
            codeValidator.validate(code)?.let { return SubmitCodeOutcome.Rejected(it) }
            val result =
                try {
                    repository.submitCode(token, codeValidator.normalise(code))
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    SubmitCodeResult.Failed(PasswordResetFailure.Unknown(UNEXPECTED_FAILURE))
                }
            return SubmitCodeOutcome.Attempted(result)
        }

        private companion object {
            const val UNEXPECTED_FAILURE = "password reset code submit threw an unexpected exception"
        }
    }
