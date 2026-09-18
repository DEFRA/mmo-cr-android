package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local

import androidx.room.Embedded
import androidx.room.Relation

/** A [GearUseEntity] aggregated with its own measurements and species/weight entries. */
data class GearUseWithChildren(
    @Embedded val gearUse: GearUseEntity,
    @Relation(parentColumn = "id", entityColumn = "gearUseId")
    val measurements: List<MeasurementEntity>,
    @Relation(parentColumn = "id", entityColumn = "gearUseId")
    val speciesWeights: List<SpeciesWeightEntity>,
)

/** The full [DraftEntity] aggregate: its gear uses (with their own children), landing/storage entries, and
 * trip-level "not landed straight away" species entries (Phase 5B). */
data class DraftWithChildren(
    @Embedded val draft: DraftEntity,
    @Relation(entity = GearUseEntity::class, parentColumn = "id", entityColumn = "draftId")
    val gearUses: List<GearUseWithChildren>,
    @Relation(parentColumn = "id", entityColumn = "draftId")
    val landingStorage: List<LandingStorageEntity>,
    @Relation(parentColumn = "id", entityColumn = "draftId")
    val notLandedSpecies: List<NotLandedSpeciesEntity>,
)
