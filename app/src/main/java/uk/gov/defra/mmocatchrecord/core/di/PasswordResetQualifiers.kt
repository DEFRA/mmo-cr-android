package uk.gov.defra.mmocatchrecord.core.di

import javax.inject.Qualifier

/** Monotonic millisecond clock (`SystemClock.elapsedRealtime`), unaffected by wall-clock changes. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class MonotonicClock

/** Minutes a password reset code stays valid, from the packaged app config (ADR 0024, ADR 0027). */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ResetCodeLifetimeMinutes

/** The browser hand-off notice stage of the password reset journey (WCAG 2.2 SC 4.1.3). */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class PasswordResetStage
