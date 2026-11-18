package uk.gov.defra.mmocatchrecord.feature.signin.data

import android.content.Context
import android.content.res.AssetManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.defra.mmocatchrecord.core.config.AppConfiguration
import java.io.ByteArrayInputStream

@OptIn(ExperimentalCoroutinesApi::class)
class NativeAuthClientWarmerTests {
    @Test
    fun `fake sign-in never warms the MSAL client`() {
        val client = FakeNativeAuthClient()

        warmerFor(client, """{"API_BASE_URL":"$BASE","USE_STUB_AUTH":true,"USE_FAKE_SIGN_IN":true}""").warmUp()

        assertEquals(0, client.warmUpCallCount)
    }

    @Test
    fun `real sign-in warms the MSAL client`() {
        val client = FakeNativeAuthClient()

        warmerFor(client, """{"API_BASE_URL":"$BASE","USE_STUB_AUTH":true,"USE_FAKE_SIGN_IN":false}""").warmUp()

        assertEquals(1, client.warmUpCallCount)
    }

    private fun warmerFor(
        client: NativeAuthClient,
        config: String,
    ): NativeAuthClientWarmer {
        val assets = mock<AssetManager>()
        whenever(assets.open("mmocr_app_config.json")).thenReturn(ByteArrayInputStream(config.toByteArray()))
        val context = mock<Context>()
        whenever(context.assets).thenReturn(assets)
        return NativeAuthClientWarmer(client, AppConfiguration(context), TestScope(UnconfinedTestDispatcher()))
    }

    private companion object {
        const val BASE = "https://example.test/"
    }
}
