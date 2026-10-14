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
    fun `schema without keys is rejected`() {
        assertThrows(IllegalStateException::class.java) { AppConfigJson.fromLocalValues("{}", emptyMap()) }
    }

    @Test
    fun `release config must be a JSON object`() {
        AppConfigJson.requireJsonObject("""{"API_BASE_URL":"https://a.example"}""")
        assertThrows(IllegalStateException::class.java) { AppConfigJson.requireJsonObject("[]") }
        assertThrows(IllegalStateException::class.java) { AppConfigJson.requireJsonObject("not json") }
    }
}
