package uk.gov.defra.mmocatchrecord.feature.home.data

import uk.gov.defra.mmocatchrecord.feature.home.domain.CatchRecordStatus
import uk.gov.defra.mmocatchrecord.feature.home.domain.CatchRecordSummary
import uk.gov.defra.mmocatchrecord.feature.home.domain.HomeRepository
import uk.gov.defra.mmocatchrecord.feature.home.domain.HomeSummary

/**
 * In-memory fake [HomeRepository] for Stage-1 wiring and tests. Replaced by a Room-backed implementation
 * (reading the offline mutation queue) in a later stage.
 */
class FakeHomeRepository(
    var result: Result<HomeSummary> =
        Result.success(
            HomeSummary(
                signedInUserId = "stub-user",
                pendingCatchRecordCount = 1,
                catchRecords =
                    listOf(
                        CatchRecordSummary("1", "20 Nov 2020", "ACHILLES", CatchRecordStatus.SUBMITTED, "J.Smith"),
                        CatchRecordSummary("2", "20 Nov 2020", "ACHILLES", CatchRecordStatus.AMENDED, "J.Smith"),
                        CatchRecordSummary("3", "20 Nov 2020", "ACHILLES", CatchRecordStatus.UNSENT, "J.Smith"),
                        CatchRecordSummary("4", "20 Nov 2020", "ACHILLES", CatchRecordStatus.LATE, "J.Smith"),
                    ),
                totalCount = 4,
                pageStart = 1,
                pageEnd = 4,
            ),
        ),
) : HomeRepository {
    override suspend fun getSummary(): Result<HomeSummary> = result
}
