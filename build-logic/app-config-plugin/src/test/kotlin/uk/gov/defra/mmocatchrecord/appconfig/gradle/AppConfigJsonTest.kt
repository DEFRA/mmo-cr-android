package uk.gov.defra.mmocatchrecord.appconfig.gradle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AppConfigJsonTest {
    private val schema =
        """
        {"keys": {
          "API_BASE_URL": {"type": "url", "required": true, "description": "Backend"},
          "USE_STUB_AUTH": {"type": "bool", "required": true, "description": "Stub auth"}
        }}
        """.trimIndent()

    @Test
    fun `writes typed values for every schema key`() {
        val result =
            AppConfigJson.fromLocalValues(
                schema,
                mapOf("API_BASE_URL" to " http://10.0.2.2:3002/ ", "USE_STUB_AUTH" to "true", "OTHER" to "x"),
            )

        assertEquals("""{"API_BASE_URL":"http://10.0.2.2:3002/","USE_STUB_AUTH":true}""", result.json)
        assertEquals(emptyList<String>(), result.missingKeys)
    }

    @Test
    fun `unset keys are written empty and reported`() {
        val result = AppConfigJson.fromLocalValues(schema, emptyMap())

        assertEquals("""{"API_BASE_URL":"","USE_STUB_AUTH":false}""", result.json)
        assertEquals(listOf("API_BASE_URL", "USE_STUB_AUTH"), result.missingKeys)
    }

    @Test
    fun `keys unused by an enabled flag are written empty and not reported`() {
        val flagged =
            """
            {"keys": {
              "USE_FAKE_SIGN_IN": {"type": "bool", "required": true, "description": "Fake"},
              "MSAL_CLIENT_ID": {"type": "string", "required": false, "description": "Id", "unusedWhen": "USE_FAKE_SIGN_IN"}
            }}
            """.trimIndent()

        val off = AppConfigJson.fromLocalValues(flagged, mapOf("USE_FAKE_SIGN_IN" to "false"))
        val on = AppConfigJson.fromLocalValues(flagged, mapOf("USE_FAKE_SIGN_IN" to "true", "MSAL_CLIENT_ID" to "x"))

        assertEquals(listOf("MSAL_CLIENT_ID"), off.missingKeys)
        assertEquals("""{"USE_FAKE_SIGN_IN":true,"MSAL_CLIENT_ID":""}""", on.json)
        assertEquals(emptyList<String>(), on.missingKeys)
    }

    @Test
    fun `schema without keys is rejected`() {
        assertThrows(IllegalStateException::class.java) { AppConfigJson.fromLocalValues("{}", emptyMap()) }
    }
}
