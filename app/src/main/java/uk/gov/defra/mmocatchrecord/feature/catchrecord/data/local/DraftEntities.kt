package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for the root [uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft]
 * aggregate. See ADR 0006 for the SQLCipher at-rest encryption wrapping this database, and ADR 0007 for how
 * this is driven by the wizard flow.
 *
 * The "one active draft per vessel" rule (draft singularity) is enforced by
 * [CatchRecordDraftDao]/the repository at the application layer within a transaction (a partial/conditional
 * unique index is not expressed by Room's annotation API), not by a SQL constraint on this table. The
 * `(vesselId, status)` index below exists purely to make that lookup query efficient.
 */
@Entity(
    tableName = "catch_record_draft",
    indices = [Index(value = ["vesselId", "status"])],
)
data class DraftEntity(
    @PrimaryKey val id: String,
    val vesselId: String,
    val isTripToday: Boolean?,
    val departureDay: Int?,
    val departureMonth: Int?,
    val departureYear: Int?,
    val returnDay: Int?,
    val returnMonth: Int?,
    val returnYear: Int?,
    val departurePortId: String?,
    val departurePortSelectionMode: String?,
    val returnPortId: String?,
    val returnPortSelectionMode: String?,
    val status: String,
    val modifiedAtEpochMillis: Long,
    /** Phase 5B: `null` (column default) until the "not landing straight away" question is answered. */
    val notLandedStraightAway: Boolean? = null,
)

/** One gear deployment within a draft trip. Cascades from [DraftEntity] on delete (FR10 invalidation). */
@Entity(
    tableName = "catch_record_gear_use",
    foreignKeys = [
        ForeignKey(
            entity = DraftEntity::class,
            parentColumns = ["id"],
            childColumns = ["draftId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["draftId"])],
)
data class GearUseEntity(
    @PrimaryKey val id: String,
    val draftId: String,
    val gearTypeId: String,
    val statisticalSubRectangleCode: String?,
    val orderIndex: Int,
    val numberOfShots: Int?,
    val confirmedUsedOnTrip: Boolean,
)

/**
 * A single gear-specific measurement key/value row. Kept as a flexible key/value shape (rather than fixed
 * columns) because gear-type-specific measurement fields are not yet confirmed (blocked on later-phase
 * screenshots) — see [uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue].
 */
@Entity(
    tableName = "catch_record_measurement",
    foreignKeys = [
        ForeignKey(
            entity = GearUseEntity::class,
            parentColumns = ["id"],
            childColumns = ["gearUseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["gearUseId"])],
)
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "rowId") val rowId: Long = 0,
    val gearUseId: String,
    val key: String,
    val numericValue: Double?,
    val unit: String?,
    val textValue: String?,
)

/** A species catch entry captured for one species within a [GearUseEntity] (Phase 5). */
@Entity(
    tableName = "catch_record_species_weight",
    foreignKeys = [
        ForeignKey(
            entity = GearUseEntity::class,
            parentColumns = ["id"],
            childColumns = ["gearUseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["gearUseId"])],
)
data class SpeciesWeightEntity(
    @PrimaryKey val id: String,
    val gearUseId: String,
    val speciesId: String,
    val weightAboveMinimumSizeKg: Double?,
    val weightBelowMinimumSizeKg: Double?,
    val weightLegallyDiscardedKg: Double?,
    val confirmedCaught: Boolean,
)

/**
 * A trip-level (not per-gear) "not landing straight away" species entry (Phase 5B). Cascades from
 * [DraftEntity] on delete, mirroring [LandingStorageEntity].
 */
@Entity(
    tableName = "catch_record_not_landed_species",
    foreignKeys = [
        ForeignKey(
            entity = DraftEntity::class,
            parentColumns = ["id"],
            childColumns = ["draftId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["draftId"])],
)
data class NotLandedSpeciesEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "rowId") val rowId: Long = 0,
    val draftId: String,
    val speciesId: String,
    val weightAboveMinimumSizeKeptOnboardKg: Double?,
)

/** A landing/storage key/value row (flexible shape — fields TBD, see domain model doc comment). */
@Entity(
    tableName = "catch_record_landing_storage",
    foreignKeys = [
        ForeignKey(
            entity = DraftEntity::class,
            parentColumns = ["id"],
            childColumns = ["draftId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["draftId"])],
)
data class LandingStorageEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "rowId") val rowId: Long = 0,
    val draftId: String,
    val entryId: String,
    val key: String,
    val value: String,
)
