package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.map

import android.content.Context
import geopipeline.GeoPrecomputePipeline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.MapGeometryDataset
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.MapGeometryRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [MapGeometryRepository] backed by the build-time-precomputed binary asset
 * (`assets/map_geometry.bin` — see `GeoPrecomputeTask`/[MapGeometryBinaryDecoder]), decoded once and cached
 * in memory (the dataset is static/read-only for the lifetime of the process — same rationale as ADR
 * 0008's stub reference data).
 *
 * **Graceful fallback to a genuine on-device GeoJSON parse — see ADR 0013.** If the derived binary asset is
 * missing or fails to decode (any [IOException] — not just [java.io.FileNotFoundException] — covers a
 * missing asset, a corrupt/truncated asset, or one that fails the [MapGeometryBinaryDecoder]'s bounds/
 * validity checks), this repository does **not** silently degrade to an empty dataset. Instead it re-parses
 * the three raw source GeoJSON files bundled as a deliberate, documented exception under
 * `assets/geo-source-fallback/` (copied there by `GeoPrecomputeTask` alongside the compact derived asset —
 * see its doc comment) using the **exact same** shared [GeoPrecomputePipeline]/`GeoJsonParser`/
 * `GeometryMath`/`GeoBinaryWriter` logic the build-time pipeline itself uses (see `shared-geopipeline/` —
 * one physical source directory compiled into both `buildSrc` and this app module, so there is no
 * duplicated/divergent parsing logic): the raw GeoJSON is run back through the pipeline into an in-memory
 * copy of the same binary format, which is then decoded by the same [MapGeometryBinaryDecoder] used for the
 * primary asset. This is slower (full on-device GeoJSON parse) but genuinely produces usable geometry rather
 * than an empty map+list.
 *
 * If the fallback GeoJSON parse **also** fails (both assets missing/corrupt — an unlikely double failure),
 * this returns an explicit [Result.failure] (a [MapGeometryUnavailableException]) rather than an empty
 * dataset, so callers (see `CatchRecordFlowViewModel`) can surface a real, accessible error state instead of
 * silently rendering a broken/empty screen.
 */
@Singleton
class AssetMapGeometryRepository
    @Inject
    constructor(
        private val context: Context,
    ) : MapGeometryRepository {
        /**
         * Overridable only for tests — production always uses `context.assets.open(...)` (the default). Lets
         * unit tests exercise the missing/corrupt-asset fallback paths deterministically, without needing a
         * real Robolectric asset directory that omits the bundled assets (which Robolectric otherwise always
         * includes from the merged debug asset set).
         */
        internal var assetOpener: (String) -> InputStream = { name -> context.assets.open(name) }

        private val cached = AtomicReference<MapGeometryDataset?>(null)

        override suspend fun getMapGeometry(): Result<MapGeometryDataset> =
            withContext(Dispatchers.IO) {
                cached.get()?.let { return@withContext Result.success(it) }

                val primaryResult = decodePrimaryAsset()
                val dataset =
                    primaryResult.getOrElse { primaryError ->
                        Timber.w(
                            primaryError,
                            "Map geometry derived asset missing/corrupt — falling back to an on-device " +
                                "GeoJSON parse.",
                        )
                        val fallbackResult = decodeFallbackGeoJson()
                        fallbackResult.getOrElse { fallbackError ->
                            Timber.e(
                                fallbackError,
                                "Map geometry GeoJSON fallback parse also failed — no usable geometry.",
                            )
                            return@withContext Result.failure(
                                MapGeometryUnavailableException(primaryError, fallbackError),
                            )
                        }
                    }
                cached.set(dataset)
                Result.success(dataset)
            }

        /** Attempts to decode the compact precomputed binary asset — the fast, normal-case path. */
        private fun decodePrimaryAsset(): Result<MapGeometryDataset> =
            try {
                assetOpener(MapGeometryBinaryDecoder.DERIVED_ASSET_FILE_NAME).use { stream ->
                    MapGeometryBinaryDecoder.decode(stream)
                }
            } catch (missingOrUnreadable: IOException) {
                // Deliberately catches the broad java.io.IOException (not just FileNotFoundException): a
                // permissions error, a corrupt asset archive, or any other I/O failure opening the asset
                // must route through the same fallback, never propagate uncaught.
                Result.failure(missingOrUnreadable)
            }

        /**
         * Re-parses the bundled raw fallback GeoJSON via the shared build-time pipeline logic, round-tripped
         * through the same binary format/decoder as the primary asset (see class doc comment) — reuses 100%
         * of the parse/reproject/sea-overlap logic with zero duplication.
         *
         * **Deliberately not `runCatching`** (finding: "`runCatching` is too broad"): a bare `runCatching`
         * would also swallow [kotlinx.coroutines.CancellationException] (breaking structured concurrency —
         * a cancelled coroutine must keep propagating cancellation, never be converted into "try the
         * expensive fallback anyway") and JVM [Error]s such as [OutOfMemoryError] (which must never be
         * caught and converted into a "safe" [Result.failure] — the process is not in a state where
         * continuing is safe). Only the specific checked/expected failure modes of this code path are
         * caught: [IOException] (asset open/read, and the round-trip binary write/read), and
         * [IllegalArgumentException] (covers [kotlinx.serialization.SerializationException], thrown by the
         * shared pipeline's GeoJSON parser on malformed JSON — see `GeoJsonParser`) /
         * [IllegalStateException] (thrown by [GeoPrecomputePipeline]'s own `check()` safety guard if far
         * fewer sub-rectangles parse than expected).
         */
        private fun decodeFallbackGeoJson(): Result<MapGeometryDataset> =
            try {
                val land = readFallbackAsset(LAND_GEOJSON_FILE_NAME)
                val subRectangles = readFallbackAsset(SUB_RECTANGLES_GEOJSON_FILE_NAME)
                val ports = readFallbackAsset(PORTS_GEOJSON_FILE_NAME)

                val binaryOutput = ByteArrayOutputStream()
                GeoPrecomputePipeline.run(
                    landGeoJson = land,
                    subRectanglesGeoJson = subRectangles,
                    portsGeoJson = ports,
                    output = binaryOutput,
                )
                val decoded =
                    MapGeometryBinaryDecoder.decode(ByteArrayInputStream(binaryOutput.toByteArray())).getOrThrow()
                Result.success(decoded)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (expected: IOException) {
                Result.failure(expected)
            } catch (expected: IllegalArgumentException) {
                Result.failure(expected)
            } catch (expected: IllegalStateException) {
                Result.failure(expected)
            }

        private fun readFallbackAsset(fileName: String): String =
            assetOpener("$FALLBACK_GEOJSON_ASSET_DIR/$fileName").bufferedReader().use { it.readText() }

        companion object {
            /** Must match `GeoPrecomputeTask.FALLBACK_GEOJSON_ASSET_DIR` (buildSrc) exactly. */
            const val FALLBACK_GEOJSON_ASSET_DIR = "geo-source-fallback"
            private const val LAND_GEOJSON_FILE_NAME = "map.geojson"
            private const val SUB_RECTANGLES_GEOJSON_FILE_NAME = "subrectangles.geojson"
            private const val PORTS_GEOJSON_FILE_NAME = "ports.geojson"
        }
    }

/**
 * Thrown (wrapped in a [Result.failure]) when both the precomputed binary asset **and** the on-device
 * GeoJSON fallback parse fail — see [AssetMapGeometryRepository]'s doc comment. Carries both underlying
 * causes for diagnostics (never shown to the user directly — see `SafeErrorMapper`).
 */
class MapGeometryUnavailableException(
    val primaryError: Throwable,
    val fallbackError: Throwable,
) : IOException(
        "Map geometry binary asset decode failed and the on-device GeoJSON fallback parse also failed",
        primaryError,
    )
