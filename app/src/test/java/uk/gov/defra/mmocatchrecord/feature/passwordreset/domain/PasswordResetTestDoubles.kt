package uk.gov.defra.mmocatchrecord.feature.passwordreset.domain

import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInSession

/** Opaque test token; its `toString()` reveals nothing, like the production wrapper. */
internal class TestResetToken(
    val label: String = "token",
) : PasswordResetToken {
    override fun toString(): String = "TestResetToken"
}

/** Hand-written [PasswordResetRepository] double that records calls and returns scripted results. */
internal class RecordingPasswordResetRepository(
    var startResult: StartResetResult = StartResetResult.CodeSent(TestResetToken()),
    var submitCodeResult: SubmitCodeResult = SubmitCodeResult.Accepted(TestResetToken("accepted")),
    var submitPasswordResult: SubmitPasswordResult = SubmitPasswordResult.Reset,
    var browserResult: BrowserContinuationResult = BrowserContinuationResult.Cancelled,
    var thrown: Throwable? = null,
) : PasswordResetRepository {
    val startedEmails = mutableListOf<String>()
    val submittedCodes = mutableListOf<String>()
    var lastPasswordReference: CharArray? = null
        private set
    var lastPasswordAtCall: String? = null
        private set
    var browserCallCount = 0
        private set

    override suspend fun start(email: String): StartResetResult {
        startedEmails += email
        thrown?.let { throw it }
        return startResult
    }

    override suspend fun submitCode(
        token: PasswordResetToken,
        code: String,
    ): SubmitCodeResult {
        submittedCodes += code
        thrown?.let { throw it }
        return submitCodeResult
    }

    override suspend fun submitPassword(
        token: PasswordResetToken,
        password: CharArray,
    ): SubmitPasswordResult {
        lastPasswordReference = password
        lastPasswordAtCall = String(password)
        thrown?.let { throw it }
        return submitPasswordResult
    }

    override suspend fun continueInBrowser(): BrowserContinuationResult {
        browserCallCount++
        return browserResult
    }
}

internal val TEST_SESSION = SignInSession(userId = "fixture-user-id", signedInAtEpochMillis = 0L)
