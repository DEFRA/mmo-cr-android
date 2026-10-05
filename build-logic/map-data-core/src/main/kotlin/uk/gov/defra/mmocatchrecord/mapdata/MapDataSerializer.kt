package uk.gov.defra.mmocatchrecord.mapdata

import kotlinx.serialization.json.Json

/** Shared JSON codec for [MapDataset] — used by both the build-time task and the app's runtime loader. */
object MapDataSerializer {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(dataset: MapDataset): String = json.encodeToString(MapDataset.serializer(), dataset)

    /** Never throws: returns `null` on any malformed/incompatible JSON so callers can fall back safely. */
    fun decodeOrNull(rawJson: String): MapDataset? =
        runCatching { json.decodeFromString(MapDataset.serializer(), rawJson) }
            .getOrNull()
            ?.takeIf { it.formatVersion == MapDataset.CURRENT_FORMAT_VERSION }
}
