package uk.gov.defra.mmocatchrecord.core.logging

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.util.ReflectionHelpers
import timber.log.Timber
import uk.gov.defra.mmocatchrecord.core.config.AppConfiguration

@RunWith(RobolectricTestRunner::class)
class StartupMetadataLoggerTests {
    private val messages = mutableListOf<String>()
    private val tree =
        object : Timber.Tree() {
            override fun log(
                priority: Int,
                tag: String?,
                message: String,
                t: Throwable?,
            ) {
                if (message.startsWith("App startup:")) messages += message
            }
        }
    private val originalSdkInt = Build.VERSION.SDK_INT

    @Before
    fun setUp() {
        Timber.uprootAll()
        Timber.plant(tree)
    }

    @After
    fun tearDown() {
        Timber.uproot(tree)
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", originalSdkInt)
    }

    @Test
    fun `logs configured api host and stub auth flag`() {
        val appConfiguration = mock<AppConfiguration>()
        whenever(appConfiguration.apiHostForStartupLog()).thenReturn("example.test")
        whenever(appConfiguration.useStubAuth).thenReturn(true)

        log(appConfiguration)

        val message = messages.single()
        assertTrue(message.contains("gitCommitSha=abc123"))
        assertTrue(message.contains("apiHost=example.test"))
        assertTrue(message.contains("useStubAuth=true"))
    }

    @Test
    fun `logs unavailable placeholders on legacy SDKs when config is missing`() {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", Build.VERSION_CODES.O)
        val appConfiguration = mock<AppConfiguration>()
        whenever(appConfiguration.useStubAuth).thenReturn(null)

        log(appConfiguration)

        val message = messages.single()
        assertTrue(message.contains("apiHost=unavailable"))
        assertTrue(message.contains("useStubAuth=unavailable"))
    }

    private fun log(appConfiguration: AppConfiguration) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        StartupMetadataLogger.log(context.packageManager, context.packageName, "abc123", appConfiguration)
    }
}
