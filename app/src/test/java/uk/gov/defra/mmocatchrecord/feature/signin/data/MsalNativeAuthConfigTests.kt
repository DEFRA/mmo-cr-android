package uk.gov.defra.mmocatchrecord.feature.signin.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM: validates [MsalNativeAuthConfig] against a build flavor's bundled MSAL config JSON. */
class MsalNativeAuthConfigTests {
    @Test
    fun `a fully provisioned config is provisioned`() {
        val json =
            """
            {"client_id": "6d6542d5-0a1f-44bc-a7d9-fd2b775800fb", "authorities": [{"type": "CIAM"}]}
            """.trimIndent()

        assertTrue(MsalNativeAuthConfig.isProvisioned(json))
    }

    @Test
    fun `the fully-placeholder prod config is not provisioned`() {
        val json = """{"client_id": "", "authorities": []}"""

        assertFalse(MsalNativeAuthConfig.isProvisioned(json))
    }

    @Test
    fun `a config with a client_id but no authorities is not provisioned`() {
        val json = """{"client_id": "6d6542d5-0a1f-44bc-a7d9-fd2b775800fb", "authorities": []}"""

        assertFalse(MsalNativeAuthConfig.isProvisioned(json))
    }

    @Test
    fun `a config with authorities but a blank client_id is not provisioned`() {
        val json = """{"client_id": "", "authorities": [{"type": "CIAM"}]}"""

        assertFalse(MsalNativeAuthConfig.isProvisioned(json))
    }

    @Test
    fun `requireProvisioned does not throw for a provisioned config`() {
        val json = """{"client_id": "6d6542d5-0a1f-44bc-a7d9-fd2b775800fb", "authorities": [{"type": "CIAM"}]}"""

        MsalNativeAuthConfig.requireProvisioned(json)
    }

    @Test(expected = MsalConfigNotProvisionedException::class)
    fun `requireProvisioned throws for the placeholder prod config`() {
        MsalNativeAuthConfig.requireProvisioned("""{"client_id": "", "authorities": []}""")
    }

    @Test
    fun `malformed json is treated as not provisioned rather than throwing a parse error`() {
        assertFalse(MsalNativeAuthConfig.isProvisioned("not valid json"))
    }
}
