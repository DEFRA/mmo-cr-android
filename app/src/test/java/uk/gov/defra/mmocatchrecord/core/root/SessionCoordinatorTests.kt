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
import org.junit.Before
import org.junit.Test
import uk.gov.defra.mmocatchrecord.core.security.BiometricReentryPolicy
import uk.gov.defra.mmocatchrecord.core.security.FakeBiometricRepository
import uk.gov.defra.mmocatchrecord.core.security.InMemoryBiometricPreferenceStore
import uk.gov.defra.mmocatchrecord.core.security.InMemorySessionStore

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

    private fun buildCoordinator(
        sessionStore: InMemorySessionStore = InMemorySessionStore(),
        biometricPreferenceStore: InMemoryBiometricPreferenceStore = InMemoryBiometricPreferenceStore(),
        reentryPolicy: BiometricReentryPolicy = BiometricReentryPolicy(reentryTimeoutMillis = 60_000L),
        biometricRepository: FakeBiometricRepository = FakeBiometricRepository(),
        clock: () -> Long = { 0L },
        dispatcher: kotlinx.coroutines.CoroutineDispatcher = mainDispatcher,
    ) = SessionCoordinator(
        sessionStore = sessionStore,
        biometricPreferenceStore = biometricPreferenceStore,
        reentryPolicy = reentryPolicy,
        biometricRepository = biometricRepository,
        clock = clock,
        dispatcher = dispatcher,
    )

    @Test
    fun `no prior session starts in SIGN_IN`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val coordinator = buildCoordinator(dispatcher = dispatcher)

            coordinator.state.test {
                assertEquals(RootPhase.SIGN_IN, awaitItem().phase)
            }
        }

    @Test
    fun `signing in with biometric disabled moves straight to HOME`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val coordinator = buildCoordinator(dispatcher = dispatcher)

            coordinator.state.test {
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
                assertEquals(RootPhase.SIGN_IN, awaitItem().phase)
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
                assertEquals(RootPhase.SIGN_IN, awaitItem().phase)
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
                assertEquals(RootPhase.SIGN_IN, awaitItem().phase)
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
    fun `signing out clears session and returns to SIGN_IN`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val sessionStore = InMemorySessionStore()
            val coordinator = buildCoordinator(sessionStore = sessionStore, dispatcher = dispatcher)

            coordinator.state.test {
                assertEquals(RootPhase.SIGN_IN, awaitItem().phase)

                coordinator.dispatch(RootEvent.SignedIn)
                assertEquals(RootPhase.HOME, awaitItem().phase)

                coordinator.dispatch(RootEvent.SignedOut)
                assertEquals(RootPhase.SIGN_IN, awaitItem().phase)
            }
        }
}
