package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.map

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Covers [AssetMapGeometryRepository]'s graceful degradation chain (see ADR 0013 / its doc comment):
 * primary precomputed binary asset -> on-device GeoJSON fallback parse -> explicit failure only if *both*
 * fail. [AssetMapGeometryRepository.assetOpener] is overridden here (a test-only seam) to force each branch
 * deterministically, since the real Robolectric test APK always includes the genuinely bundled assets
 * (there is no "asset absent" Robolectric environment to exploit otherwise) — the fallback-success tests
 * below instead redirect only the *primary* asset name to fail while leaving the real bundled fallback
 * GeoJSON assets to be read genuinely (not a fixture), exercising the real shared parse/reproject/
 * sea-overlap pipeline end-to-end.
 */
@RunWith(RobolectricTestRunner::class)
class AssetMapGeometryRepositoryTests {
    private fun realFallbackGeoJsonOpener(context: android.content.Context): (String) -> java.io.InputStream =
        { name -> context.assets.open(name) }

    @Test
    fun missingPrimaryAssetFallsBackToParsingTheRealBundledGeoJsonSuccessfully() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val repository = AssetMapGeometryRepository(context)
            val realOpener = realFallbackGeoJsonOpener(context)
            repository.assetOpener = { name ->
                if (name == MapGeometryBinaryDecoder.DERIVED_ASSET_FILE_NAME) {
                    throw FileNotFoundException("no such asset in this test")
                } else {
                    realOpener(name)
                }
            }

            val result = repository.getMapGeometry()

            assertTrue(result.isSuccess)
            val dataset = result.getOrThrow()
            assertTrue(dataset.landPolygons.isNotEmpty())
            assertTrue(dataset.subRectangles.isNotEmpty())
            assertTrue(dataset.ports.isNotEmpty())
        }

    @Test
    fun corruptPrimaryAssetFallsBackToParsingTheRealBundledGeoJsonSuccessfully() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val repository = AssetMapGeometryRepository(context)
            val realOpener = realFallbackGeoJsonOpener(context)
            repository.assetOpener = { name ->
                if (name == MapGeometryBinaryDecoder.DERIVED_ASSET_FILE_NAME) {
                    "not a valid geometry asset".byteInputStream()
                } else {
                    realOpener(name)
                }
            }

            val result = repository.getMapGeometry()

            assertTrue(result.isSuccess)
            val dataset = result.getOrThrow()
            assertTrue(dataset.landPolygons.isNotEmpty())
            assertTrue(dataset.subRectangles.isNotEmpty())
            assertTrue(dataset.ports.isNotEmpty())
        }

    @Test
    fun aGenericIOExceptionOpeningThePrimaryAssetIsHandledByTheSameFallbackRatherThanPropagatingUncaught() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val repository = AssetMapGeometryRepository(context)
            val realOpener = realFallbackGeoJsonOpener(context)
            repository.assetOpener = { name ->
                if (name == MapGeometryBinaryDecoder.DERIVED_ASSET_FILE_NAME) {
                    throw IOException("permission denied / corrupt asset archive entry")
                } else {
                    realOpener(name)
                }
            }

            val result = repository.getMapGeometry()

            assertTrue(result.isSuccess)
            val dataset = result.getOrThrow()
            assertTrue(dataset.subRectangles.isNotEmpty())
        }

    @Test
    fun bothThePrimaryAssetAndTheGeoJsonFallbackFailingProducesAnExplicitFailureNotAnEmptyDataset() =
        runTest {
            val repository = AssetMapGeometryRepository(ApplicationProvider.getApplicationContext())
            repository.assetOpener = { throw FileNotFoundException("no such asset in this test") }

            val result = repository.getMapGeometry()

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is MapGeometryUnavailableException)
        }

    @Test
    fun aFailedLoadIsNotCachedSoARepeatedCallReattemptsBothTheAssetAndFallbackReads() =
        runTest {
            var openCount = 0
            val repository = AssetMapGeometryRepository(ApplicationProvider.getApplicationContext())
            repository.assetOpener = {
                openCount++
                throw FileNotFoundException("no such asset in this test")
            }

            val first = repository.getMapGeometry()
            val openCountAfterFirst = openCount
            val second = repository.getMapGeometry()

            assertFalse(first.isSuccess)
            assertEquals(first.isSuccess, second.isSuccess)
            assertTrue("a repeated call after a failure should re-attempt the reads", openCount > openCountAfterFirst)
        }

    /**
     * Finding: "`runCatching` is too broad" — neither [AssetMapGeometryRepository.getMapGeometry] nor the
     * fallback path it triggers may swallow a [CancellationException] into a [Result.failure]/successful
     * fallback attempt. Standard Kotlin coroutines convention: a cancelled coroutine must keep propagating
     * cancellation. This asserts the exception genuinely propagates out of the suspend call, not converted
     * into any [Result].
     */
    @Test
    fun aCancellationExceptionOpeningThePrimaryAssetPropagatesRatherThanBeingConvertedToAResult() =
        runTest {
            val repository = AssetMapGeometryRepository(ApplicationProvider.getApplicationContext())
            repository.assetOpener = { throw CancellationException("coroutine cancelled") }

            try {
                repository.getMapGeometry()
                fail("expected a CancellationException to propagate, not be converted to a Result")
            } catch (expected: CancellationException) {
                assertEquals("coroutine cancelled", expected.message)
            }
        }

    /**
     * Finding: "`runCatching` is too broad" — asserts the fix in `decodeFallbackGeoJson` too: a
     * [CancellationException] thrown while reading the *fallback* GeoJSON assets (after the primary binary
     * asset has already failed) must also propagate, not be swallowed into a
     * [MapGeometryUnavailableException]/[Result.failure].
     */
    @Test
    fun aCancellationExceptionDuringTheGeoJsonFallbackReadAlsoPropagatesRatherThanBeingConvertedToAResult() =
        runTest {
            val repository = AssetMapGeometryRepository(ApplicationProvider.getApplicationContext())
            repository.assetOpener = { name ->
                if (name == MapGeometryBinaryDecoder.DERIVED_ASSET_FILE_NAME) {
                    throw FileNotFoundException("no such asset in this test")
                } else {
                    throw CancellationException("coroutine cancelled during fallback read")
                }
            }

            try {
                repository.getMapGeometry()
                fail("expected a CancellationException to propagate, not be converted to a Result")
            } catch (expected: CancellationException) {
                assertEquals("coroutine cancelled during fallback read", expected.message)
            }
        }

    @Test
    fun loadsTheRealBundledDerivedGeometryAssetSuccessfullyWithNonEmptyLayers() =
        runTest {
            // Exercises the genuine happy path against the real precomputed asset merged into the debug
            // build (see GeoPrecomputeTask/precomputeMapGeometry) — not a fixture.
            val repository = AssetMapGeometryRepository(ApplicationProvider.getApplicationContext())

            val dataset = repository.getMapGeometry().getOrThrow()

            assertTrue(dataset.landPolygons.isNotEmpty())
            assertTrue(dataset.subRectangles.isNotEmpty())
            assertTrue(dataset.ports.isNotEmpty())
        }
}
