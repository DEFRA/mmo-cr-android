package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow

import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.GeoBoundingBox
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.GeoPoint
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.MapGeometryDataset
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.MapGeometryRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.StatisticalSubRectangleGeometry

/**
 * A tiny, deterministic [MapGeometryRepository] test double for [CatchRecordFlowViewModelTests] — mirrors
 * [FakeReferenceDataRepository]'s conventions (constructor-injected canned results, default happy path).
 */
class FakeMapGeometryRepository(
    private val result: Result<MapGeometryDataset> = Result.success(defaultDataset()),
) : MapGeometryRepository {
    /** Number of times [getMapGeometry] has been called — lets tests assert against-double-loading guards. */
    var callCount: Int = 0
        private set

    override suspend fun getMapGeometry(): Result<MapGeometryDataset> {
        callCount++
        return result
    }

    companion object {
        fun defaultDataset(): MapGeometryDataset =
            MapGeometryDataset(
                landPolygons = emptyList(),
                subRectangles =
                    listOf(
                        StatisticalSubRectangleGeometry(
                            subCode = "38E95",
                            parentIcesName = "38E9",
                            rings = listOf(listOf(GeoPoint(50.0, 1.0), GeoPoint(50.1, 1.0), GeoPoint(50.1, 1.1))),
                            bboxCentroid = GeoPoint(50.05, 1.05),
                            boundingBox = GeoBoundingBox(50.0, 50.1, 1.0, 1.1),
                            seaOverlapping = true,
                        ),
                    ),
                ports = emptyList(),
            )
    }
}
