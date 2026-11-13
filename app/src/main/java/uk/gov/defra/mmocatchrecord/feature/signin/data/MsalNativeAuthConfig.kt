package uk.gov.defra.mmocatchrecord.feature.signin.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Thrown when a build flavor's bundled MSAL config is the unprovisioned placeholder (`prod` ships
 * `{ "client_id": "", "authorities": [] }` until Entra app registration lands), so sign-in fails fast
 * instead of calling MSAL with no client id and surfacing an opaque [NativeAuthOutcome.Failed].
 */
internal class MsalConfigNotProvisionedException :
    IllegalStateException(
        "MSAL config has no client_id/authorities for this build flavor",
    )

/**
 * Validates the bundled MSAL native-auth config JSON before it reaches MSAL — mirrors
 * [uk.gov.defra.mmocatchrecord.core.network.ReferenceDataServiceConfig]'s fail-fast convention for an
 * unprovisioned prod build flavor. Pure JVM: no Android dependency, so it is plain-JUnit-testable.
 */
internal object MsalNativeAuthConfig {
    fun isProvisioned(configJson: String): Boolean =
        runCatching {
            val root = Json.parseToJsonElement(configJson).jsonObject
            val clientId = root["client_id"]?.jsonPrimitive?.content.orEmpty()
            val authorities = root["authorities"]?.jsonArray ?: JsonArray(emptyList())
            clientId.isNotBlank() && authorities.isNotEmpty()
        }.getOrDefault(false)

    /** @throws MsalConfigNotProvisionedException if [configJson] is the unprovisioned placeholder. */
    fun requireProvisioned(configJson: String) {
        if (!isProvisioned(configJson)) throw MsalConfigNotProvisionedException()
    }
}
