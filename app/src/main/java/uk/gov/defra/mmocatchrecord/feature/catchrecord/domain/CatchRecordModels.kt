package uk.gov.defra.mmocatchrecord.feature.catchrecord.domain

/** Domain model for a single catch record entry. */
data class CatchRecord(
    val id: String,
    val species: String,
    val weightKg: Double,
    val recordedAtEpochMillis: Long,
)

/**
 * Repository abstraction over catch record persistence.
 *
 * **Real implementation contract (later stage):** must be Room-backed and offline-first — writes are
 * always accepted locally first and queued for sync via WorkManager, never blocked on connectivity.
 */
interface CatchRecordRepository {
    suspend fun saveCatchRecord(record: CatchRecord): Result<Unit>

    suspend fun getCatchRecords(): Result<List<CatchRecord>>
}
