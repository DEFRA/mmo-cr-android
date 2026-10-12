package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Cached mirror of a live reference-data `vessels` mobile-view item (ADR 0016). */
@Entity(tableName = "reference_data_vessel")
data class ReferenceDataVesselEntity(
    @PrimaryKey val id: String,
    val name: String,
    val displayName: String,
    val pln: String?,
    val cfr: String?,
    val lengthOverallMetres: Double?,
)

/** Cached mirror of a live reference-data `ports` mobile-view item; `coordinate` may be absent. */
@Entity(tableName = "reference_data_port")
data class ReferenceDataPortEntity(
    @PrimaryKey val id: String,
    val code: String,
    val name: String,
    val displayName: String,
    val latitude: Double?,
    val longitude: Double?,
    val statisticalAreaId: String,
)

/** Cached mirror of a live reference-data `species` mobile-view item, resolved for one [languageTag]. */
@Entity(tableName = "reference_data_species")
data class ReferenceDataSpeciesEntity(
    @PrimaryKey val id: String,
    val faoCode: String,
    val scientificName: String?,
    val displayName: String,
)

/**
 * A cached `gears` mobile-view item for one vessel-length band (`under-10m`/`10-to-12m`/`over-12m`) — ADR
 * 0015. Keyed by `(id, vesselLengthBand)`: the same gear legitimately appears in every band's response.
 */
@Entity(tableName = "reference_data_gear", primaryKeys = ["id", "vesselLengthBand"])
data class ReferenceDataGearEntity(
    val id: String,
    val vesselLengthBand: String,
    val code: String,
    val name: String,
    val categoryId: String,
    val categoryCode: String,
    val categoryName: String,
    val pairFishing: Boolean,
    val orderIndex: Int,
)

/**
 * One resolved measurement field for a [ReferenceDataGearEntity], already translated to the persisted
 * [uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementField.key] space.
 */
@Entity(
    tableName = "reference_data_gear_measurement",
    foreignKeys = [
        ForeignKey(
            entity = ReferenceDataGearEntity::class,
            parentColumns = ["id", "vesselLengthBand"],
            childColumns = ["gearId", "vesselLengthBand"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["gearId", "vesselLengthBand"])],
)
data class ReferenceDataGearMeasurementEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "rowId") val rowId: Long = 0,
    val gearId: String,
    val vesselLengthBand: String,
    val key: String,
    val label: String,
    val type: String,
    val unit: String?,
    val orderIndex: Int,
)

/**
 * One row per cached dataset (`vessels`, `ports`, `species`, or `gears:<band>`), recording manifest/response
 * caching metadata (ADR 0016). [languageTag] is only meaningful for `species` — its cache-invalidation key.
 */
@Entity(tableName = "reference_data_metadata")
data class ReferenceDataMetadataEntity(
    @PrimaryKey val datasetKey: String,
    val collectionId: String,
    val schemaVersion: String,
    val manifestVersion: String?,
    val manifestEtag: String?,
    val responseETag: String?,
    val fetchedAtEpochMillis: Long,
    val languageTag: String? = null,
)
