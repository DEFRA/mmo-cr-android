package uk.gov.defra.mmocatchrecord.feature.home.data

import uk.gov.defra.mmocatchrecord.feature.home.domain.HomeRepository
import uk.gov.defra.mmocatchrecord.feature.home.domain.HomeSummary

/**
 * In-memory fake [HomeRepository] for Stage-1 wiring and tests. Replaced by a Room-backed implementation
 * (reading the offline mutation queue) in a later stage.
 */
class FakeHomeRepository(
    var result: Result<HomeSummary> =
        Result.success(HomeSummary(signedInUserId = "stub-user", pendingCatchRecordCount = 0)),
) : HomeRepository {
    override suspend fun getSummary(): Result<HomeSummary> = result
}
