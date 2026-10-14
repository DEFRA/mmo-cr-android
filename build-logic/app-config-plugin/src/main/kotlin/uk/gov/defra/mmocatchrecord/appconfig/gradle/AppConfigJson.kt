package uk.gov.defra.mmocatchrecord.appconfig.gradle

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** The packaged stage configuration asset (ADR 0017); its keys come from config/app-config.schema.json. */
object AppConfigJson {
    const val ASSET_NAME = "mmocr_app_config.json"
    const val LOCAL_PROPERTY_PREFIX = "crAppCfg."

    data class Result(
        val json: String,
        val missingKeys: List<String>,
    )

    /** Every schema key is written; unset keys stay empty so the app reports its configuration error. */
    fun fromLocalValues(
        schemaText: String,
        values: Map<String, String>,
    ): Result {
        val keys =
            (Json.parseToJsonElement(schemaText) as? JsonObject)?.get("keys") as? JsonObject
                ?: error("App config schema must have a \"keys\" object")
        val missing = mutableListOf<String>()
        val config =
            buildJsonObject {
                keys.forEach { (key, spec) ->
                    val raw = values[key]?.trim().orEmpty()
                    if (raw.isEmpty()) missing += key
                    when (spec.jsonObject["type"]?.jsonPrimitive?.content) {
                        "bool" -> put(key, raw == "true")
                        else -> put(key, raw)
                    }
                }
            }
        return Result(config.toString(), missing)
    }

    fun requireJsonObject(text: String) {
        val parsed = runCatching { Json.parseToJsonElement(text) }.getOrNull()
        check(parsed is JsonObject) { "The release app config must be a JSON object" }
    }
}
