package uk.gov.defra.mmocatchrecord.core.android

import android.app.Activity
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/** Option D (ADR 0019 Phase 2.1c): a [WeakReference]-backed [CurrentActivityProvider]. */
@RunWith(RobolectricTestRunner::class)
class CurrentActivityProviderImplTests {
    @Test
    fun `resuming an Activity makes it the current Activity`() {
        val provider = CurrentActivityProviderImpl()
        val activity =
            Robolectric
                .buildActivity(Activity::class.java)
                .create()
                .start()
                .resume()
                .get()

        provider.onActivityResumed(activity)

        assertSame(activity, provider.current())
    }

    @Test
    fun `pausing the resumed Activity clears it`() {
        val provider = CurrentActivityProviderImpl()
        val activity =
            Robolectric
                .buildActivity(Activity::class.java)
                .create()
                .start()
                .resume()
                .get()
        provider.onActivityResumed(activity)

        provider.onActivityPaused(activity)

        assertNull(provider.current())
    }

    @Test
    fun `pausing a different Activity does not clear the resumed one`() {
        val provider = CurrentActivityProviderImpl()
        val resumed =
            Robolectric
                .buildActivity(Activity::class.java)
                .create()
                .start()
                .resume()
                .get()
        val otherController =
            Robolectric
                .buildActivity(Activity::class.java)
                .create()
                .start()
                .resume()
        provider.onActivityResumed(resumed)

        provider.onActivityPaused(otherController.get())

        assertSame(resumed, provider.current())
    }

    @Test
    @Suppress("ExplicitGarbageCollectionCall")
    fun `a garbage-collected Activity is reported as no current Activity`() {
        // A mock, not a Robolectric-built Activity: Robolectric's own registry can keep the latter
        // strongly reachable, which would make this assertion flaky rather than prove the WeakReference.
        val provider = CurrentActivityProviderImpl()
        resumeAndDropReference(provider)

        System.gc()
        System.runFinalization()
        System.gc()

        assertNull(provider.current())
    }

    /** Kept out of the test body so no local variable keeps the Activity reachable from the stack. */
    private fun resumeAndDropReference(provider: CurrentActivityProviderImpl) {
        provider.onActivityResumed(mock<Activity>())
    }
}
