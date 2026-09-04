package uk.gov.defra.mmocatchrecord.feature.catchrecord.data

import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.CatchRecord
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.CatchRecordRepository

/**
 * In-memory fake [CatchRecordRepository] for Stage-1 wiring and tests. Not persisted across process
 * death — replaced by a Room-backed, offline-first implementation (with a WorkManager sync queue) in a
 * later stage.
 */
class FakeCatchRecordRepository(
    private val records: MutableList<CatchRecord> = mutableListOf(),
) : CatchRecordRepository {
    override suspend fun saveCatchRecord(record: CatchRecord): Result<Unit> {
        records.add(record)
        return Result.success(Unit)
    }

    override suspend fun getCatchRecords(): Result<List<CatchRecord>> = Result.success(records.toList())
}
