package uk.gov.defra.mmocatchrecord.feature.signin.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInStage

/**
 * Announced browser hand-off shared by sign-in and reset (WCAG 4.1.3): publish stage, await ack, then run.
 */
internal class BrowserHandoff(
    private val nativeAuthClient: NativeAuthClient,
) {
    private val mutableStage = MutableStateFlow(SignInStage.Idle)
    val stage: StateFlow<SignInStage> = mutableStage.asStateFlow()

    @Volatile
    private var pendingBrowserNoticeAck: CompletableDeferred<Unit>? = null

    fun acknowledgeBrowserNoticeShown() {
        pendingBrowserNoticeAck?.complete(Unit)
    }

    suspend fun run(): NativeAuthOutcome {
        mutableStage.value = SignInStage.RedirectingToBrowser
        val acknowledgement = CompletableDeferred<Unit>()
        pendingBrowserNoticeAck = acknowledgement
        return try {
            withTimeoutOrNull(ACK_TIMEOUT_MILLIS) { acknowledgement.await() }
            // ~1.2s: comfortably inside the requested 1-1.5s range for a TalkBack announcement to start.
            delay(POST_ACK_PAUSE_MILLIS)
            nativeAuthClient.signInInteractively(listOf(API_SCOPE))
        } finally {
            pendingBrowserNoticeAck = null
            mutableStage.value = SignInStage.Idle
        }
    }

    private companion object {
        const val ACK_TIMEOUT_MILLIS = 3_000L
        const val POST_ACK_PAUSE_MILLIS = 1_200L
    }
}
