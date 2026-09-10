package uk.gov.defra.mmocatchrecord.feature.home.domain

import javax.inject.Inject

/** Domain summary shown on the Home screen. */
data class HomeSummary(
    val signedInUserId: String,
    val pendingCatchRecordCount: Int,
    val catchRecords: List<CatchRecordSummary> = emptyList(),
    val totalCount: Int = 0,
    val pageStart: Int = 1,
    val pageEnd: Int = 4,
)

data class CatchRecordSummary(
    val id: String,
    val tripEndDate: String,
    val vesselName: String,
    val status: CatchRecordStatus,
    val createdBy: String,
)

enum class CatchRecordStatus {
    SUBMITTED,
    AMENDED,
    UNSENT,
    LATE,
}

/**
 * Repository abstraction over Home-screen summary data (pending offline records, sync status, etc).
 *
 * **Real implementation contract (later stage):** must read from the Room-backed offline mutation queue
 * (see `core.persistence`, added in a later stage) so the pending-record count is accurate offline.
 */
interface HomeRepository {
    suspend fun getSummary(): Result<HomeSummary>
}

/** Use-case wrapping [HomeRepository.getSummary]. */
class GetHomeSummaryUseCase
    @Inject
    constructor(
        private val repository: HomeRepository,
    ) {
        suspend operator fun invoke(): Result<HomeSummary> = repository.getSummary()
    }
