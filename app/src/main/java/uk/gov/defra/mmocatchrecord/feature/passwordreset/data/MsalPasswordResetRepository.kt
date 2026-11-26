package uk.gov.defra.mmocatchrecord.feature.passwordreset.data

import kotlinx.coroutines.flow.StateFlow
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.BrowserContinuationResult
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.PasswordResetFailure
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.PasswordResetRepository
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.PasswordResetToken
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.StartResetResult
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.SubmitCodeResult
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.SubmitPasswordResult
import uk.gov.defra.mmocatchrecord.feature.signin.data.BrowserHandoff
import uk.gov.defra.mmocatchrecord.feature.signin.data.NativeAuthOutcome
import uk.gov.defra.mmocatchrecord.feature.signin.data.NativePasswordResetClient
import uk.gov.defra.mmocatchrecord.feature.signin.data.NativeResetOutcome
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInSession
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInStage
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInStageObserver
import java.util.Arrays

/**
 * MSAL-free [PasswordResetRepository] over [NativePasswordResetClient] (ADR 0027). Holds no reset state:
 * the caller owns the [PasswordResetToken]. Also the [SignInStageObserver] for the browser hand-off notice.
 */
internal class MsalPasswordResetRepository(
    private val client: NativePasswordResetClient,
    private val browserHandoff: BrowserHandoff,
) : PasswordResetRepository,
    SignInStageObserver {
    override val stage: StateFlow<SignInStage> = browserHandoff.stage

    override fun acknowledgeBrowserNoticeShown() {
        browserHandoff.acknowledgeBrowserNoticeShown()
    }

    override suspend fun start(email: String): StartResetResult =
        when (val outcome = client.startPasswordReset(email)) {
            is NativeResetOutcome.CodeRequired -> StartResetResult.CodeSent(outcome.token)
            NativeResetOutcome.BrowserRequired -> StartResetResult.BrowserRequired
            else -> StartResetResult.Failed(outcome.toFailure())
        }

    override suspend fun submitCode(
        token: PasswordResetToken,
        code: String,
    ): SubmitCodeResult =
        when (val outcome = client.submitResetCode(token, code)) {
            is NativeResetOutcome.PasswordRequired -> SubmitCodeResult.Accepted(outcome.token)
            NativeResetOutcome.InvalidCode -> SubmitCodeResult.InvalidCode
            NativeResetOutcome.ExpiredSession -> SubmitCodeResult.Expired
            NativeResetOutcome.BrowserRequired -> SubmitCodeResult.BrowserRequired
            else -> SubmitCodeResult.Failed(outcome.toFailure())
        }

    override suspend fun submitPassword(
        token: PasswordResetToken,
        password: CharArray,
    ): SubmitPasswordResult =
        try {
            when (val outcome = client.submitNewPassword(token, password)) {
                NativeResetOutcome.Completed -> SubmitPasswordResult.Reset
                NativeResetOutcome.InvalidPassword -> SubmitPasswordResult.PasswordRejected
                NativeResetOutcome.ExpiredSession -> SubmitPasswordResult.Expired
                NativeResetOutcome.BrowserRequired -> SubmitPasswordResult.BrowserRequired
                else -> SubmitPasswordResult.Failed(outcome.toFailure())
            }
        } finally {
            Arrays.fill(password, '\u0000')
        }

    override suspend fun continueInBrowser(): BrowserContinuationResult =
        when (val outcome = browserHandoff.run()) {
            is NativeAuthOutcome.Succeeded ->
                BrowserContinuationResult.SignedIn(SignInSession(userId = outcome.userId, signedInAtEpochMillis = 0L))
            NativeAuthOutcome.BrowserCancelled -> BrowserContinuationResult.Cancelled
            NativeAuthOutcome.NetworkUnavailable -> BrowserContinuationResult.Failed(PasswordResetFailure.Network)
            // A second browser_required after the browser leg is terminal, never retried (no loop).
            NativeAuthOutcome.BrowserRequired -> unknownBrowserFailure(BROWSER_REQUIRED_LOOP)
            is NativeAuthOutcome.Failed -> unknownBrowserFailure(outcome.safeDiagnostic)
            else -> unknownBrowserFailure(UNEXPECTED_BROWSER_OUTCOME)
        }

    private fun unknownBrowserFailure(diagnostic: String) =
        BrowserContinuationResult.Failed(PasswordResetFailure.Unknown(diagnostic))

    /** Outcomes that are not a step's success or specific result collapse to a safe failure. */
    private fun NativeResetOutcome.toFailure(): PasswordResetFailure =
        when (this) {
            NativeResetOutcome.NetworkUnavailable -> PasswordResetFailure.Network
            NativeResetOutcome.Throttled -> PasswordResetFailure.Throttled
            is NativeResetOutcome.Failed -> PasswordResetFailure.Unknown(safeDiagnostic)
            else -> PasswordResetFailure.Unknown(UNEXPECTED_OUTCOME)
        }

    private companion object {
        const val UNEXPECTED_OUTCOME = "password reset outcome did not belong to this step"
        const val UNEXPECTED_BROWSER_OUTCOME = "browser leg returned an outcome that is not a sign-in result"
        const val BROWSER_REQUIRED_LOOP = "native-auth required the browser leg again after the browser leg"
    }
}
