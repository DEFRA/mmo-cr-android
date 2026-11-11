package uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata

/** Lets a JVM-testable caller (e.g. [uk.gov.defra.mmocatchrecord.core.root.SessionCoordinator]) trigger a
 * reference-data refresh without depending on WorkManager directly. */
fun interface ReferenceDataRefreshScheduler {
    fun scheduleRefreshAfterSignIn()
}
