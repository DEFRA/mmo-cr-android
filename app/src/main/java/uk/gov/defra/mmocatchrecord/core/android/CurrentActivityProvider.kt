package uk.gov.defra.mmocatchrecord.core.android

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/** Supplies the current foreground [Activity] to data-layer code that needs one (e.g. MSAL's browser leg). */
fun interface CurrentActivityProvider {
    fun current(): Activity?
}

/**
 * [CurrentActivityProvider] backed by a [WeakReference] kept current via [Application.ActivityLifecycleCallbacks],
 * so it never holds a strong reference past the Activity's own lifecycle (Option D, ADR 0019 Phase 2.1c).
 */
@Singleton
class CurrentActivityProviderImpl
    @Inject
    constructor() :
    CurrentActivityProvider,
        Application.ActivityLifecycleCallbacks {
        @Volatile
        private var resumedActivity: WeakReference<Activity>? = null

        override fun current(): Activity? = resumedActivity?.get()

        override fun onActivityResumed(activity: Activity) {
            resumedActivity = WeakReference(activity)
        }

        override fun onActivityPaused(activity: Activity) {
            // Only clear if it's still this instance - an overlapping resume/pause must not drop the new one.
            if (resumedActivity?.get() === activity) {
                resumedActivity = null
            }
        }

        override fun onActivityCreated(
            activity: Activity,
            savedInstanceState: Bundle?,
        ) = Unit

        override fun onActivityStarted(activity: Activity) = Unit

        override fun onActivityStopped(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(
            activity: Activity,
            outState: Bundle,
        ) = Unit

        override fun onActivityDestroyed(activity: Activity) = Unit
    }
