package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.map

import android.content.Context
import android.content.res.AssetManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.MapDataUnavailableException
import java.io.ByteArrayInputStream
import java.io.IOException

private const val EMPTY_FEATURE_COLLECTION = "{\"type\":\"FeatureCollection\",\"features\":[]}"
private const val VALID_GENERATED_ASSET =
    "{\"formatVersion\":1,\"land\":[],\"subRectangles\":[],\"ports\":[]}"
private const val CORRUPT_GENERATED_ASSET = "{ not valid json"
private const val VERSION_MISMATCHED_ASSET =
    "{\"formatVersion\":999,\"land\":[],\"subRectangles\":[],\"ports\":[]}"

/**
 * Unit tests for [AssetMapDataRepository]'s "generated asset primary, on-device parse fallback, never
 * crash" contract (requirement #4) — using a mocked [Context]/[AssetManager] rather than Robolectric so
 * every asset-read outcome (present/missing/corrupt/version-mismatched) can be deterministically forced.
 */
class AssetMapDataRepositoryTests {
    private fun contextReturning(assetContents: Map<String, String>): Context {
        val assetManager = mock<AssetManager>()
        assetContents.forEach { (path, contents) ->
            whenever(assetManager.open(path)).thenReturn(ByteArrayInputStream(contents.toByteArray()))
        }
        val context = mock<Context>()
        whenever(context.assets).thenReturn(assetManager)
        // Any path not explicitly stubbed above throws, simulating "file missing".
        listOf(
            "map_data/dataset.json",
            "map_data/fallback/map.geojson",
            "map_data/fallback/subrectangles.geojson",
            "map_data/fallback/ports.geojson",
        ).filterNot { assetContents.containsKey(it) }.forEach {
            whenever(assetManager.open(it)).thenThrow(IOException("missing: $it"))
        }
        return context
    }

    @Test
    fun `loads the generated asset when present and valid`() =
        runTest {
            val context = contextReturning(mapOf("map_data/dataset.json" to VALID_GENERATED_ASSET))
            val repository = AssetMapDataRepository(context)

            val result = repository.loadDataset()

            assertTrue(result.isSuccess)
            assertEquals(1, result.getOrNull()?.formatVersion)
        }

    @Test
    fun `falls back to on-device parsing when the generated asset is missing`() =
        runTest {
            val context =
                contextReturning(
                    mapOf(
                        "map_data/fallback/map.geojson" to EMPTY_FEATURE_COLLECTION,
                        "map_data/fallback/subrectangles.geojson" to EMPTY_FEATURE_COLLECTION,
                        "map_data/fallback/ports.geojson" to EMPTY_FEATURE_COLLECTION,
                    ),
                )
            val repository = AssetMapDataRepository(context)

            val result = repository.loadDataset()

            assertTrue(result.isSuccess)
            assertEquals(1, result.getOrNull()?.formatVersion)
        }

    @Test
    fun `falls back to on-device parsing when the generated asset is corrupt`() =
        runTest {
            val context =
                contextReturning(
                    mapOf(
                        "map_data/dataset.json" to CORRUPT_GENERATED_ASSET,
                        "map_data/fallback/map.geojson" to EMPTY_FEATURE_COLLECTION,
                        "map_data/fallback/subrectangles.geojson" to EMPTY_FEATURE_COLLECTION,
                        "map_data/fallback/ports.geojson" to EMPTY_FEATURE_COLLECTION,
                    ),
                )
            val repository = AssetMapDataRepository(context)

            val result = repository.loadDataset()

            assertTrue(result.isSuccess)
        }

    @Test
    fun `falls back to on-device parsing when the generated asset is a stale format version`() =
        runTest {
            val context =
                contextReturning(
                    mapOf(
                        "map_data/dataset.json" to VERSION_MISMATCHED_ASSET,
                        "map_data/fallback/map.geojson" to EMPTY_FEATURE_COLLECTION,
                        "map_data/fallback/subrectangles.geojson" to EMPTY_FEATURE_COLLECTION,
                        "map_data/fallback/ports.geojson" to EMPTY_FEATURE_COLLECTION,
                    ),
                )
            val repository = AssetMapDataRepository(context)

            val result = repository.loadDataset()

            assertTrue(result.isSuccess)
        }

    @Test
    fun `returns a failure and never throws when both the generated asset and the fallback fail`() =
        runTest {
            val context = contextReturning(emptyMap())
            val repository = AssetMapDataRepository(context)

            val result = repository.loadDataset()

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is MapDataUnavailableException)
        }

    @Test
    fun `caches the result so the asset is only read once per process`() =
        runTest {
            val context = contextReturning(mapOf("map_data/dataset.json" to VALID_GENERATED_ASSET))
            val repository = AssetMapDataRepository(context)

            val first = repository.loadDataset()
            val second = repository.loadDataset()

            assertSame(first.getOrNull(), second.getOrNull())
        }

    /**
     * Regression test: a transient failure (e.g. a momentary asset-read problem) must not be cached —
     * only successes are cached — so calling `retry()`/[AssetMapDataRepository.loadDataset] again after a
     * failure can succeed once the underlying read starts working, and that retried success is then
     * itself cached for subsequent calls.
     */
    @Test
    fun `retries after a failure and succeeds, caching the retried result`() =
        runTest {
            val assetManager = mock<AssetManager>()
            listOf(
                "map_data/dataset.json",
                "map_data/fallback/map.geojson",
                "map_data/fallback/subrectangles.geojson",
                "map_data/fallback/ports.geojson",
            ).forEach { path ->
                whenever(assetManager.open(path)).thenThrow(IOException("missing: $path"))
            }
            val context = mock<Context>()
            whenever(context.assets).thenReturn(assetManager)
            val repository = AssetMapDataRepository(context)

            val first = repository.loadDataset()

            assertTrue(first.isFailure)
            assertTrue(first.exceptionOrNull() is MapDataUnavailableException)

            // The underlying read now succeeds — a fresh stream per call, as a real AssetManager would
            // return. `doAnswer(...).whenever(...)` (rather than `whenever(...).thenAnswer { }`) avoids
            // invoking the still-throwing existing stub while re-stubbing it.
            doAnswer { ByteArrayInputStream(VALID_GENERATED_ASSET.toByteArray()) }
                .whenever(assetManager)
                .open("map_data/dataset.json")

            val second = repository.loadDataset()
            val third = repository.loadDataset()

            assertTrue(second.isSuccess)
            assertSame(second.getOrNull(), third.getOrNull())
        }
}
