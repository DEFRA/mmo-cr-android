package uk.gov.defra.mmocatchrecord.feature.passwordreset.domain

import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/** Hands the reset off to the system browser after a `BrowserRequired` result; exceptions become a safe failure. */
class ContinueResetInBrowserUseCase
    @Inject
    constructor(
        private val repository: PasswordResetRepository,
    ) {
        @Suppress("TooGenericExceptionCaught")
        suspend operator fun invoke(): BrowserContinuationResult =
            try {
                repository.continueInBrowser()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                BrowserContinuationResult.Failed(PasswordResetFailure.Unknown(UNEXPECTED_FAILURE))
            }

        private companion object {
            const val UNEXPECTED_FAILURE = "password reset browser hand-off threw an unexpected exception"
        }
    }
