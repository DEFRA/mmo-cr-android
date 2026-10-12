package uk.gov.defra.mmocatchrecord.feature.home.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.ReferenceDataRepository
import uk.gov.defra.mmocatchrecord.feature.home.domain.HomeRepository
import uk.gov.defra.mmocatchrecord.feature.home.domain.HomeSummary
import javax.inject.Inject

private const val STUB_SIGNED_IN_USER_ID = "stub-user"

/**
 * Room-backed [HomeRepository] — see ADR 0014 Phase B. [STUB_SIGNED_IN_USER_ID]: no session store yet.
 * Resolves each record's [uk.gov.defra.mmocatchrecord.feature.home.domain.CatchRecordSummary.vesselId]
 * to its [ReferenceDataRepository] display name (e.g. "ACHILLES") so the Home table never shows a raw
 * vessel id; an id with no matching reference-data vessel falls back to the raw id rather than blanking.
 */
class RoomHomeRepository
    @Inject
    constructor(
        private val draftRepository: CatchRecordDraftRepository,
        private val referenceDataRepository: ReferenceDataRepository,
    ) : HomeRepository {
        override fun observeSummary(): Flow<HomeSummary> =
            flow {
                val vesselNamesById =
                    referenceDataRepository.getVessels().getOrDefault(emptyList()).associate { it.id to it.name }
                draftRepository.observeRecordSummaries().collect { records ->
                    val resolved =
                        records.map { record ->
                            record.copy(vesselName = vesselNamesById[record.vesselId] ?: record.vesselId)
                        }
                    emit(HomeSummary(signedInUserId = STUB_SIGNED_IN_USER_ID, catchRecords = resolved))
                }
            }
    }
