package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.map

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.MapDataRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.MapDataUnavailableException
import uk.gov.defra.mmocatchrecord.mapdata.MapDataGenerator
import uk.gov.defra.mmocatchrecord.mapdata.MapDataSerializer
import uk.gov.defra.mmocatchrecord.mapdata.MapDataset
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads [MapDataset] from the build-generated `assets/map_data/dataset.json` (see
 * `uk.gov.defra.mmocatchrecord.mapdata.gradle.GenerateMapDataTask`), off the main thread, cached once per
 * process. Falls back to parsing the bundled raw source GeoJSON copies under
 * `assets/map_data/fallback/` (e.g. `map.geojson`) on-device if the generated asset is missing, corrupt, or a stale
 * format version — see docs/development/offline-map.md. Never throws: every failure path is logged (no
 * PII/secrets — see ADR 0011) and surfaced as a [Result] failure.
 */
@Singleton
class AssetMapDataRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : MapDataRepository {
        private val mutex = Mutex()

        @Volatile
        private var cached: MapDataset? = null

        override suspend fun loadDataset(): Result<MapDataset> {
            cached?.let { return Result.success(it) }
            return mutex.withLock {
                cached?.let { return@withLock Result.success(it) }
                val result = withContext(Dispatchers.IO) { loadFromDisk() }
                result.onSuccess { cached = it }
                result
            }
        }

        private fun loadFromDisk(): Result<MapDataset> {
            loadGeneratedAsset()?.let { return Result.success(it) }
            Timber.w("MapData: generated asset missing/corrupt/stale — falling back to on-device parse")
            loadFallbackParse()?.let { return Result.success(it) }
            val failure = MapDataUnavailableException("Offline map data unavailable (asset and fallback both failed)")
            Timber.e(failure, "MapData: unable to load dataset from either source")
            return Result.failure(failure)
        }

        private fun loadGeneratedAsset(): MapDataset? =
            try {
                val raw =
                    context.assets
                        .open(GENERATED_ASSET_PATH)
                        .bufferedReader()
                        .use { it.readText() }
                MapDataSerializer.decodeOrNull(raw)
            } catch (e: IOException) {
                Timber.w(e, "MapData: could not read generated asset")
                null
            }

        private fun loadFallbackParse(): MapDataset? =
            try {
                val land = readFallbackAsset("map.geojson")
                val subRectangles = readFallbackAsset("subrectangles.geojson")
                val ports = readFallbackAsset("ports.geojson")
                MapDataGenerator.generate(land, subRectangles, ports).dataset
            } catch (e: IOException) {
                Timber.e(e, "MapData: fallback on-device parse failed")
                null
            }

        private fun readFallbackAsset(fileName: String): String =
            context.assets
                .open("$FALLBACK_ASSET_DIR/$fileName")
                .bufferedReader()
                .use { it.readText() }

        private companion object {
            const val GENERATED_ASSET_PATH = "map_data/dataset.json"
            const val FALLBACK_ASSET_DIR = "map_data/fallback"
        }
    }
