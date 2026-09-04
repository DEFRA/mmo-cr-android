package uk.gov.defra.mmocatchrecord.feature.map.data

import uk.gov.defra.mmocatchrecord.feature.map.domain.CatchLocation
import uk.gov.defra.mmocatchrecord.feature.map.domain.MapRepository

/**
 * In-memory fake [MapRepository] for Stage-1 wiring and tests. Returns an empty list by default —
 * replaced by a Room-backed implementation once the map SDK/rendering approach is confirmed.
 */
class FakeMapRepository(
    var result: Result<List<CatchLocation>> = Result.success(emptyList()),
) : MapRepository {
    override suspend fun getCatchLocations(): Result<List<CatchLocation>> = result
}
