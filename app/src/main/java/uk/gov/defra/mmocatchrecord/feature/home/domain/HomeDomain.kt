package uk.gov.defra.mmocatchrecord.feature.home.domain

import kotlinx.coroutines.flow.Flow
import uk.gov.defra.mmocatchrecord.common.design.RecordStatusTag
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import javax.inject.Inject

/** Domain summary shown on the Home screen; [pendingCatchRecordCount] is derived, see its getter. */
data class HomeSummary(
    val signedInUserId: String,
    val catchRecords: List<CatchRecordSummary> = emptyList(),
) {
    val pendingCatchRecordCount: Int
        get() = catchRecords.count { it.status == RecordStatusTag.AwaitingSync }
}

/**
 * [tripEndDate] is `null` until the return date is entered (legitimate for a [RecordStatusTag.Draft]).
 * [vesselName] is the vessel's reference-data display name (e.g. "ACHILLES") resolved from [vesselId] by
 * [uk.gov.defra.mmocatchrecord.feature.home.data.RoomHomeRepository]; it defaults to the raw [vesselId]
 * so an unresolved id is never silently blanked.
 */
data class CatchRecordSummary(
    val id: String,
    val catchRecordReference: String?,
    val vesselId: String,
    val tripEndDate: DmyDate?,
    val status: RecordStatusTag,
    val vesselName: String = vesselId,
)

/** Reactive repository abstraction over Home-screen summary data — see ADR 0014 Phase B. */
interface HomeRepository {
    fun observeSummary(): Flow<HomeSummary>
}

/** Use-case wrapping [HomeRepository.observeSummary]. */
class ObserveHomeSummaryUseCase
    @Inject
    constructor(
        private val repository: HomeRepository,
    ) {
        operator fun invoke(): Flow<HomeSummary> = repository.observeSummary()
    }
