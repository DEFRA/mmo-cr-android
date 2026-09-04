package uk.gov.defra.mmocatchrecord.feature.home.domain

/** Domain summary shown on the Home screen. */
data class HomeSummary(
    val signedInUserId: String,
    val pendingCatchRecordCount: Int,
)

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
class GetHomeSummaryUseCase(
    private val repository: HomeRepository,
) {
    suspend operator fun invoke(): Result<HomeSummary> = repository.getSummary()
}
