package uk.gov.defra.mmocatchrecord.core.root

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import uk.gov.defra.mmocatchrecord.core.security.BiometricReentryPolicy
import uk.gov.defra.mmocatchrecord.core.security.FakeBiometricRepository
import uk.gov.defra.mmocatchrecord.core.security.InMemoryBiometricPreferenceStore
import uk.gov.defra.mmocatchrecord.core.security.InMemorySessionStore
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.ReferenceDataRefreshScheduler
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignOutRepository

/**
 * [SessionCoordinator] tests using fakes (no Android framework dependencies needed) + coroutines-test,
 * asserting [RootPhase] transitions emitted on its [kotlinx.coroutines.flow.StateFlow].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionCoordinatorTests {
    private lateinit var mainDispatcher: TestDispatcher

    @Before
    fun setUp() {
        mainDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Suppress("LongParameterList")
    private fun buildCoordinator(
        sessionStore: InMemorySessionStore = InMemorySessionStore(),
        biometricPreferenceStore: InMemoryBiometricPreferenceStore = InMemoryBiometricPreferenceStore(),
        reentryPolicy: BiometricReentryPolicy = BiometricReentryPolicy(reentryTimeoutMillis = 60_000L),
        biometricRepository: FakeBiometricRepository = FakeBiometricRepository(),
        signOutRepository: FakeSignOutRepository = FakeSignOutRepository(),
        referenceDataRefreshScheduler: FakeReferenceDataRefreshScheduler = FakeReferenceDataRefreshScheduler(),
        clock: () -> Long = { 0L },
        dispatcher: kotlinx.coroutines.CoroutineDispatcher = mainDispatcher,
    ) = SessionCoordinator(
        sessionStore = sessionStore,
        biometricPreferenceStore = biometricPreferenceStore,
        reentryPolicy = reentryPolicy,
        biometricRepository = biometricRepository,
        signOutRepository = signOutRepository,
        referenceDataRefreshScheduler = referenceDataRefreshScheduler,
        clock = clock,
        dispatcher = dispatcher,
    )

    @Test
    fun `no prior session starts in SPLASH then resolves to SIGN_IN`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val coordinator = buildCoordinator(dispatcher = dispatcher)

            coordinator.state.test {
                assertEquals(RootPhase.SPLASH, awaitItem().phase)
                assertEquals(RootPhase.SIGN_IN, awaitItem().phase)
            }
        }

    @Test
    fun `signing in with biometric disabled moves straight to HOME`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val coordinator = buildCoordinator(dispatcher = dispatcher)

            coordinator.state.test {
                assertEquals(RootPhase.SPLASH, awaitItem().phase)
                assertEquals(RootPhase.SIGN_IN, awaitItem().phase)

                coordinator.dispatch(RootEvent.SignedIn)

                assertEquals(RootPhase.HOME, awaitItem().phase)
            }
        }

    @Test
    fun `signing in with biometric enabled and no completed reentry stays locked`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val sessionStore = InMemorySessionStore()
            val biometricPreferenceStore = InMemoryBiometricPreferenceStore(initiallyEnabled = true)
            var now = 0L
            val coordinator =
                buildCoordinator(
                    sessionStore = sessionStore,
                    biometricPreferenceStore = biometricPreferenceStore,
                    clock = { now },
                    dispatcher = dispatcher,
                )

            coordinator.state.test {
                assertEquals(RootPhase.SPLASH, awaitItem().phase)
                assertEquals(RootPhase.SIGN_IN, awaitItem().phase)

                now = 1_000L
                coordinator.dispatch(RootEvent.SignedIn)

                // Just authenticated: within the reentry timeout window, so straight to HOME.
                assertEquals(RootPhase.HOME, awaitItem().phase)
            }
        }

    @Test
    fun `expired biometric session requires APP_LOCK on refresh`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val sessionStore = InMemorySessionStore()
            sessionStore.recordAuthenticatedNow(0L)
            val biometricPreferenceStore = InMemoryBiometricPreferenceStore(initiallyEnabled = true)
            val coordinator =
                buildCoordinator(
                    sessionStore = sessionStore,
                    biometricPreferenceStore = biometricPreferenceStore,
                    reentryPolicy = BiometricReentryPolicy(reentryTimeoutMillis = 60_000L),
                    clock = { 120_000L },
                    dispatcher = dispatcher,
                )

            coordinator.state.test {
                assertEquals(RootPhase.SPLASH, awaitItem().phase)
                assertEquals(RootPhase.APP_LOCK, awaitItem().phase)
            }
        }

    @Test
    fun `biometric unlock event returns to HOME`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val sessionStore = InMemorySessionStore()
            sessionStore.recordAuthenticatedNow(0L)
            val biometricPreferenceStore = InMemoryBiometricPreferenceStore(initiallyEnabled = true)
            var now = 120_000L
            val coordinator =
                buildCoordinator(
                    sessionStore = sessionStore,
                    biometricPreferenceStore = biometricPreferenceStore,
                    reentryPolicy = BiometricReentryPolicy(reentryTimeoutMillis = 60_000L),
                    clock = { now },
                    dispatcher = dispatcher,
                )

            coordinator.state.test {
                assertEquals(RootPhase.SPLASH, awaitItem().phase)
                assertEquals(RootPhase.APP_LOCK, awaitItem().phase)

                coordinator.dispatch(RootEvent.BiometricUnlockRequested)

                assertEquals(RootPhase.HOME, awaitItem().phase)
            }
        }

    @Test
    fun `failed biometric unlock attempt stays locked`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val sessionStore = InMemorySessionStore()
            sessionStore.recordAuthenticatedNow(0L)
            val biometricPreferenceStore = InMemoryBiometricPreferenceStore(initiallyEnabled = true)
            val failingBiometricRepository =
                FakeBiometricRepository(
                    authenticationResult = Result.failure(IllegalStateException("user cancelled")),
                )
            val now = 120_000L
            val coordinator =
                buildCoordinator(
                    sessionStore = sessionStore,
                    biometricPreferenceStore = biometricPreferenceStore,
                    reentryPolicy = BiometricReentryPolicy(reentryTimeoutMillis = 60_000L),
                    biometricRepository = failingBiometricRepository,
                    clock = { now },
                    dispatcher = dispatcher,
                )

            coordinator.state.test {
                assertEquals(RootPhase.SPLASH, awaitItem().phase)
                assertEquals(RootPhase.APP_LOCK, awaitItem().phase)

                coordinator.dispatch(RootEvent.BiometricUnlockRequested)

                // The session store was never updated, so re-evaluating still yields APP_LOCK. Assert
                // directly on current state (no *new* emission is expected since the phase is unchanged).
                assertEquals(RootPhase.APP_LOCK, coordinator.state.value.phase)
            }
        }

    @Test
    fun `app resumed after timeout elapses re-locks even without a new sign-in`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val sessionStore = InMemorySessionStore()
            val biometricPreferenceStore = InMemoryBiometricPreferenceStore(initiallyEnabled = true)
            var now = 0L
            val coordinator =
                buildCoordinator(
                    sessionStore = sessionStore,
                    biometricPreferenceStore = biometricPreferenceStore,
                    reentryPolicy = BiometricReentryPolicy(reentryTimeoutMillis = 60_000L),
                    clock = { now },
                    dispatcher = dispatcher,
                )

            coordinator.state.test {
                assertEquals(RootPhase.SPLASH, awaitItem().phase)
                assertEquals(RootPhase.SIGN_IN, awaitItem().phase)

                coordinator.dispatch(RootEvent.SignedIn)
                assertEquals(RootPhase.HOME, awaitItem().phase)

                // Simulate time passing while the app sits in the background, then coming back to the
                // foreground after the re-entry timeout has elapsed.
                now = 200_000L
                coordinator.dispatch(RootEvent.AppResumed)

                assertEquals(RootPhase.APP_LOCK, awaitItem().phase)
            }
        }

    @Test
    fun `signing in records both the app-lock and the online verification timestamps`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val sessionStore = InMemorySessionStore()
            val coordinator =
                buildCoordinator(sessionStore = sessionStore, clock = { 1_000L }, dispatcher = dispatcher)

            coordinator.state.test {
                assertEquals(RootPhase.SPLASH, awaitItem().phase)
                assertEquals(RootPhase.SIGN_IN, awaitItem().phase)

                coordinator.dispatch(RootEvent.SignedIn)
                assertEquals(RootPhase.HOME, awaitItem().phase)
            }

            assertEquals(1_000L, sessionStore.getLastAuthenticatedAtMillis())
            assertEquals(1_000L, sessionStore.getLastVerifiedOnlineAtMillis())
        }

    @Test
    fun `biometric unlock refreshes app-lock re-entry but never the online verification timestamp`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val sessionStore = InMemorySessionStore()
            sessionStore.recordAuthenticatedNow(0L)
            sessionStore.recordVerifiedOnlineNow(0L)
            val biometricPreferenceStore = InMemoryBiometricPreferenceStore(initiallyEnabled = true)
            val coordinator =
                buildCoordinator(
                    sessionStore = sessionStore,
                    biometricPreferenceStore = biometricPreferenceStore,
                    reentryPolicy = BiometricReentryPolicy(reentryTimeoutMillis = 60_000L),
                    clock = { 120_000L },
                    dispatcher = dispatcher,
                )

            coordinator.state.test {
                assertEquals(RootPhase.SPLASH, awaitItem().phase)
                assertEquals(RootPhase.APP_LOCK, awaitItem().phase)

                coordinator.dispatch(RootEvent.BiometricUnlockRequested)
                assertEquals(RootPhase.HOME, awaitItem().phase)
            }

            assertEquals(120_000L, sessionStore.getLastAuthenticatedAtMillis())
            // A local gesture must never renew a remote authorisation decision (ADR 0019).
            assertEquals(0L, sessionStore.getLastVerifiedOnlineAtMillis())
        }

    @Test
    fun `a failed biometric unlock advances neither timestamp`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val sessionStore = InMemorySessionStore()
            sessionStore.recordAuthenticatedNow(0L)
            sessionStore.recordVerifiedOnlineNow(0L)
            val coordinator =
                buildCoordinator(
                    sessionStore = sessionStore,
                    biometricPreferenceStore = InMemoryBiometricPreferenceStore(initiallyEnabled = true),
                    reentryPolicy = BiometricReentryPolicy(reentryTimeoutMillis = 60_000L),
                    biometricRepository =
                        FakeBiometricRepository(
                            authenticationResult = Result.failure(IllegalStateException("user cancelled")),
                        ),
                    clock = { 120_000L },
                    dispatcher = dispatcher,
                )

            coordinator.state.test {
                assertEquals(RootPhase.SPLASH, awaitItem().phase)
                assertEquals(RootPhase.APP_LOCK, awaitItem().phase)

                coordinator.dispatch(RootEvent.BiometricUnlockRequested)
                assertEquals(RootPhase.APP_LOCK, coordinator.state.value.phase)
            }

            assertEquals(0L, sessionStore.getLastAuthenticatedAtMillis())
            assertEquals(0L, sessionStore.getLastVerifiedOnlineAtMillis())
        }

    @Test
    fun `signing out clears session and returns to SIGN_IN`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val sessionStore = InMemorySessionStore()
            val signOutRepository = FakeSignOutRepository()
            val scheduler = FakeReferenceDataRefreshScheduler()
            val coordinator =
                buildCoordinator(
                    sessionStore = sessionStore,
                    signOutRepository = signOutRepository,
                    referenceDataRefreshScheduler = scheduler,
                    dispatcher = dispatcher,
                )

            coordinator.state.test {
                assertEquals(RootPhase.SPLASH, awaitItem().phase)
                assertEquals(RootPhase.SIGN_IN, awaitItem().phase)

                coordinator.dispatch(RootEvent.SignedIn)
                assertEquals(RootPhase.HOME, awaitItem().phase)

                coordinator.dispatch(RootEvent.SignedOut)
                assertEquals(RootPhase.SIGN_IN, awaitItem().phase)
            }

            assertNull(sessionStore.getLastAuthenticatedAtMillis())
            assertNull(sessionStore.getLastVerifiedOnlineAtMillis())
            assertEquals(1, signOutRepository.signOutCallCount)
            // Only the SignedIn event scheduled a refresh; SignedOut must not schedule a second one.
            assertEquals(1, scheduler.scheduleCallCount)
        }

    @Test
    fun `signing in schedules exactly one reference-data refresh`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val scheduler = FakeReferenceDataRefreshScheduler()
            val coordinator =
                buildCoordinator(referenceDataRefreshScheduler = scheduler, dispatcher = dispatcher)

            coordinator.state.test {
                assertEquals(RootPhase.SPLASH, awaitItem().phase)
                assertEquals(RootPhase.SIGN_IN, awaitItem().phase)

                coordinator.dispatch(RootEvent.SignedIn)
                assertEquals(RootPhase.HOME, awaitItem().phase)
            }

            assertEquals(1, scheduler.scheduleCallCount)
        }

    @Test
    fun `biometric re-entry does not schedule a reference-data refresh`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val sessionStore = InMemorySessionStore()
            sessionStore.recordAuthenticatedNow(0L)
            val biometricPreferenceStore = InMemoryBiometricPreferenceStore(initiallyEnabled = true)
            val scheduler = FakeReferenceDataRefreshScheduler()
            val coordinator =
                buildCoordinator(
                    sessionStore = sessionStore,
                    biometricPreferenceStore = biometricPreferenceStore,
                    reentryPolicy = BiometricReentryPolicy(reentryTimeoutMillis = 60_000L),
                    referenceDataRefreshScheduler = scheduler,
                    clock = { 120_000L },
                    dispatcher = dispatcher,
                )

            coordinator.state.test {
                assertEquals(RootPhase.SPLASH, awaitItem().phase)
                assertEquals(RootPhase.APP_LOCK, awaitItem().phase)

                coordinator.dispatch(RootEvent.BiometricUnlockRequested)
                assertEquals(RootPhase.HOME, awaitItem().phase)
            }

            assertEquals(0, scheduler.scheduleCallCount)
        }

    @Test
    fun `a scheduling failure on sign-in does not stop the phase transition`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val scheduler = FakeReferenceDataRefreshScheduler(throwOnSchedule = IllegalStateException("boom"))
            val coordinator =
                buildCoordinator(referenceDataRefreshScheduler = scheduler, dispatcher = dispatcher)

            coordinator.state.test {
                assertEquals(RootPhase.SPLASH, awaitItem().phase)
                assertEquals(RootPhase.SIGN_IN, awaitItem().phase)

                coordinator.dispatch(RootEvent.SignedIn)

                // Sign-in still completes even though scheduling the refresh failed.
                assertEquals(RootPhase.HOME, awaitItem().phase)
            }

            assertEquals(1, scheduler.scheduleCallCount)
        }

    @Test
    fun `a failed native-auth sign-out still clears the app session and returns to SIGN_IN`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val sessionStore = InMemorySessionStore()
            val signOutRepository = FakeSignOutRepository(signOutResult = false)
            val coordinator =
                buildCoordinator(
                    sessionStore = sessionStore,
                    signOutRepository = signOutRepository,
                    dispatcher = dispatcher,
                )

            coordinator.state.test {
                assertEquals(RootPhase.SPLASH, awaitItem().phase)
                assertEquals(RootPhase.SIGN_IN, awaitItem().phase)

                coordinator.dispatch(RootEvent.SignedIn)
                assertEquals(RootPhase.HOME, awaitItem().phase)

                coordinator.dispatch(RootEvent.SignedOut)
                assertEquals(RootPhase.SIGN_IN, awaitItem().phase)
            }

            // User intent to sign out always wins locally, even though MSAL's own clear failed.
            assertNull(sessionStore.getLastAuthenticatedAtMillis())
            assertEquals(1, signOutRepository.signOutCallCount)
        }
}

/** Tracks invocations so sign-out tests can assert MSAL's cached account is actually cleared. */
private class FakeSignOutRepository(
    private val signOutResult: Boolean = true,
) : SignOutRepository {
    var signOutCallCount: Int = 0
        private set

    override suspend fun signOut(): Boolean {
        signOutCallCount++
        return signOutResult
    }
}

/** Tracks invocations, and can simulate a scheduling failure, without touching WorkManager. */
private class FakeReferenceDataRefreshScheduler(
    private val throwOnSchedule: Throwable? = null,
) : ReferenceDataRefreshScheduler {
    var scheduleCallCount: Int = 0
        private set

    override fun scheduleRefreshAfterSignIn() {
        scheduleCallCount++
        throwOnSchedule?.let { throw it }
    }
}
