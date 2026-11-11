package uk.gov.defra.mmocatchrecord.feature.signin.data

import android.app.Activity
import android.content.Context
import com.microsoft.identity.client.AcquireTokenParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.nativeauth.INativeAuthPublicClientApplication
import com.microsoft.identity.nativeauth.parameters.NativeAuthGetAccessTokenParameters
import com.microsoft.identity.nativeauth.parameters.NativeAuthSignInParameters
import com.microsoft.identity.nativeauth.statemachine.errors.SignInError
import com.microsoft.identity.nativeauth.statemachine.results.GetAccessTokenResult
import com.microsoft.identity.nativeauth.statemachine.results.GetAccountResult
import com.microsoft.identity.nativeauth.statemachine.results.SignInResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import uk.gov.defra.mmocatchrecord.core.android.CurrentActivityProvider
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The only file permitted to import `com.microsoft.identity.*` (ADR 0019); maps every MSAL
 * result/error onto [NativeAuthOutcome] via public predicates only — never an SDK message.
 */
@Suppress("TooGenericExceptionCaught")
internal class MsalNativeAuthClient(
    private val context: Context,
    private val configResId: Int,
    private val currentActivityProvider: CurrentActivityProvider,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) : NativeAuthClient {
    private val initMutex = Mutex()

    @Volatile
    private var cachedClient: INativeAuthPublicClientApplication? = null

    private suspend fun client(): INativeAuthPublicClientApplication =
        cachedClient ?: initMutex.withLock {
            cachedClient ?: PublicClientApplication
                .createNativeAuthPublicClientApplication(context, configResId)
                .also { cachedClient = it }
        }

    override suspend fun signIn(
        username: String,
        password: CharArray,
        scopes: List<String>,
    ): NativeAuthOutcome =
        withContext(ioDispatcher) {
            try {
                val parameters =
                    NativeAuthSignInParameters(username = username).apply {
                        this.password = password
                        this.scopes = scopes
                    }
                when (val result = client().signIn(parameters)) {
                    is SignInResult.Complete -> NativeAuthOutcome.Succeeded(result.resultValue.getAccount().id)
                    is SignInResult.CodeRequired -> NativeAuthOutcome.CodeRequired
                    // Forced password change routes to the browser web fallback (2.1c) by user decision.
                    is SignInResult.PasswordRequired -> NativeAuthOutcome.BrowserRequired
                    is SignInResult.MFARequired -> NativeAuthOutcome.BrowserRequired
                    is SignInResult.StrongAuthMethodRegistrationRequired -> NativeAuthOutcome.BrowserRequired
                    is SignInError -> result.toOutcome()
                    else -> NativeAuthOutcome.Failed(UNMAPPED_SIGN_IN_RESULT)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (thrown: Exception) {
                thrown.toOutcome(SIGN_IN_THREW)
            }
        }

    override suspend fun signInInteractively(scopes: List<String>): NativeAuthOutcome {
        val activity = currentActivityProvider.current() ?: return NativeAuthOutcome.Failed(NO_FOREGROUND_ACTIVITY)
        return try {
            val client = client()
            val result = withContext(mainDispatcher) { acquireTokenInteractive(client, activity, scopes) }
            NativeAuthOutcome.Succeeded(result.account.id)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: BrowserSignInCancelledException) {
            NativeAuthOutcome.BrowserCancelled
        } catch (thrown: Exception) {
            thrown.toOutcome(INTERACTIVE_SIGN_IN_THREW)
        }
    }

    /** Bridges MSAL's callback-based interactive flow; `acquireToken` must be invoked on the main thread. */
    private suspend fun acquireTokenInteractive(
        client: INativeAuthPublicClientApplication,
        activity: Activity,
        scopes: List<String>,
    ): IAuthenticationResult =
        suspendCancellableCoroutine { continuation ->
            val callback =
                object : AuthenticationCallback {
                    override fun onSuccess(authenticationResult: IAuthenticationResult) {
                        if (continuation.isActive) continuation.resume(authenticationResult)
                    }

                    override fun onError(exception: MsalException) {
                        if (continuation.isActive) continuation.resumeWithException(exception)
                    }

                    override fun onCancel() {
                        if (continuation.isActive) continuation.resumeWithException(BrowserSignInCancelledException())
                    }
                }
            // MSAL exposes no cancel API for an in-flight interactive request; this only detaches our
            // callback, it can't dismiss an already-open Custom Tab.
            continuation.invokeOnCancellation {}
            client.acquireToken(
                AcquireTokenParameters
                    .Builder()
                    .startAuthorizationFromActivity(activity)
                    .withScopes(scopes)
                    .withCallback(callback)
                    .build(),
            )
        }

    override suspend fun currentAccountUserId(): String? =
        withContext(ioDispatcher) {
            try {
                (client().getCurrentAccount() as? GetAccountResult.AccountFound)?.resultValue?.getAccount()?.id
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (thrown: Exception) {
                Timber.w("currentAccountUserId failed: %s", thrown::class.simpleName)
                null
            }
        }

    override suspend fun accessToken(
        scopes: List<String>,
        forceRefresh: Boolean,
    ): String? =
        withContext(ioDispatcher) {
            try {
                val account =
                    (client().getCurrentAccount() as? GetAccountResult.AccountFound)?.resultValue
                        ?: return@withContext null
                val parameters =
                    NativeAuthGetAccessTokenParameters().apply {
                        this.scopes = scopes
                        this.forceRefresh = forceRefresh
                    }
                (account.getAccessToken(parameters) as? GetAccessTokenResult.Complete)
                    ?.resultValue
                    ?.accessToken
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (thrown: Exception) {
                Timber.w("accessToken failed: %s", thrown::class.simpleName)
                null
            }
        }

    override suspend fun signOut() {
        withContext(ioDispatcher) {
            try {
                (client().getCurrentAccount() as? GetAccountResult.AccountFound)?.resultValue?.signOut()
                Unit
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (thrown: Exception) {
                Timber.w("signOut failed: %s", thrown::class.simpleName)
            }
        }
    }

    // Credential rows are fused: isUserNotFound() alone would make this an account-enumeration oracle.
    private fun SignInError.toOutcome(): NativeAuthOutcome =
        when {
            isUserNotFound() || isInvalidCredentials() || isInvalidUsername() ->
                NativeAuthOutcome.InvalidCredentialsOrUnknownAccount
            isBrowserRequired() -> NativeAuthOutcome.BrowserRequired
            hasNetworkCause(exception) -> NativeAuthOutcome.NetworkUnavailable
            else -> NativeAuthOutcome.Failed(SAFE_SIGN_IN_ERROR)
        }

    private fun Exception.toOutcome(fallback: String): NativeAuthOutcome =
        if (hasNetworkCause(this)) NativeAuthOutcome.NetworkUnavailable else NativeAuthOutcome.Failed(fallback)

    private fun hasNetworkCause(thrown: Throwable?): Boolean {
        var cause = thrown
        while (cause != null) {
            if (cause is IOException) return true
            cause = cause.cause
        }
        return false
    }

    /** Internal marker for [AuthenticationCallback.onCancel] — never a [CancellationException] (see onCancel). */
    private class BrowserSignInCancelledException : Exception()

    private companion object {
        const val UNMAPPED_SIGN_IN_RESULT = "unmapped native-auth sign-in result"
        const val SIGN_IN_THREW = "native-auth client threw during sign-in"
        const val SAFE_SIGN_IN_ERROR = "unclassified native-auth sign-in error"
        const val INTERACTIVE_SIGN_IN_THREW = "native-auth client threw during interactive sign-in"
        const val NO_FOREGROUND_ACTIVITY = "no foreground activity for the interactive browser leg"
    }
}
