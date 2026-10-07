package uk.gov.defra.mmocatchrecord.feature.home.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.home.domain.HomeRepository
import uk.gov.defra.mmocatchrecord.feature.home.domain.HomeSummary
import javax.inject.Inject

private const val STUB_SIGNED_IN_USER_ID = "stub-user"

/** Room-backed [HomeRepository] — see ADR 0014 Phase B. [STUB_SIGNED_IN_USER_ID]: no session store yet. */
class RoomHomeRepository
    @Inject
    constructor(
        private val draftRepository: CatchRecordDraftRepository,
    ) : HomeRepository {
        override fun observeSummary(): Flow<HomeSummary> =
            draftRepository.observeRecordSummaries().map { records ->
                HomeSummary(signedInUserId = STUB_SIGNED_IN_USER_ID, catchRecords = records)
            }
    }
