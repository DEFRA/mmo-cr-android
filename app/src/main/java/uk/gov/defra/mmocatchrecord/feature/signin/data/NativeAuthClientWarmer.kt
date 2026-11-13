package uk.gov.defra.mmocatchrecord.feature.signin.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uk.gov.defra.mmocatchrecord.core.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pre-warms [NativeAuthClient] off the main thread at app start (review fix 1) — fixes the root cause
 * of the signed-in token stall rather than only masking it with the signed-out fast path.
 */
@Singleton
class NativeAuthClientWarmer
    @Inject
    internal constructor(
        private val nativeAuthClient: NativeAuthClient,
        @ApplicationScope private val applicationScope: CoroutineScope,
    ) {
        fun warmUp() {
            applicationScope.launch { nativeAuthClient.warmUp() }
        }
    }
