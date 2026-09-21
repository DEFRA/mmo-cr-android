package uk.gov.defra.mmocatchrecord.core.connectivity

import kotlinx.coroutines.flow.Flow

/**
 * Continuous, real-time observation of device network connectivity — used to drive the persistent
 * "Offline" banner shown on every catch-record wizard screen (see `ConnectivityViewModel` and
 * `CatchRecordWizardScaffold`). Deliberately separate from [NetworkConnectivityChecker], which stays a
 * synchronous point-in-time check used only at submit time (ADR 0009); this is ongoing observation for as
 * long as a wizard screen is on screen.
 */
interface ConnectivityObserver {
    /** Emits the current connectivity state immediately, then again whenever it changes. */
    val isOnline: Flow<Boolean>
}
