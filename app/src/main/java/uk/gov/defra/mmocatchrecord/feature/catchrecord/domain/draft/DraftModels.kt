package uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft

/**
 * Status of a [CatchRecordDraft]. Only [Draft] and [ReadyToSubmit] are non-terminal ("active") — an
 * active draft is what [CatchRecordDraftRepository] enforces at most one of, per vessel (the confirmed
 * "draft singularity" assumption). [Submitted] and [Discarded] are terminal and never block starting a
 * new draft for the same vessel.
 */
enum class DraftStatus {
    Draft,
    ReadyToSubmit,
    Submitted,
    Discarded,
    ;

    /** Whether this status counts as the vessel's single "active" (in-progress) draft. */
    val isActive: Boolean
        get() = this == Draft || this == ReadyToSubmit
}

/** How the user selected a port: typed/searched for the first time, or picked from their favourites. */
enum class PortSelectionMode {
    FirstTime,
    Favourite,
}

/**
 * A single day-month-year date as entered by the user, kept as plain fields (not a platform `LocalDate`)
 * so the domain module has no Android/JVM-time-API version constraints and is trivially unit-testable.
 */
data class DmyDate(
    val day: Int,
    val month: Int,
    val year: Int,
) : Comparable<DmyDate> {
    override fun compareTo(other: DmyDate): Int =
        compareValuesBy(this, other, DmyDate::year, DmyDate::month, DmyDate::day)
}

/** A port selection made by the user for either the departure or return leg of the trip. */
data class PortSelection(
    val portId: String,
    val selectionMode: PortSelectionMode,
)

/** A single measurement value recorded against a [GearUse], typed by the shape of the underlying input. */
sealed interface MeasurementValue {
    data class Numeric(
        val value: Double,
        val unit: String,
    ) : MeasurementValue

    data class Text(
        val value: String,
    ) : MeasurementValue
}

/** A retained-above/retained-below/discarded weight entry for one species within a [GearUse]. */
data class SpeciesWeightEntry(
    val id: String,
    val speciesId: String,
    val retainedAboveMcrsKg: Double,
    val retainedBelowMcrsKg: Double,
    val discardedKg: Double,
)

/**
 * One gear deployment within a trip: its gear type, gear-specific measurements (keyed by a
 * gear-type-defined field name, since exact fields per gear type are not yet confirmed), its own
 * statistical sub-rectangle selection, and its own species/weight entries.
 */
data class GearUse(
    val id: String,
    val gearTypeId: String,
    val statRectangleId: String?,
    val measurements: Map<String, MeasurementValue> = emptyMap(),
    val speciesWeights: List<SpeciesWeightEntry> = emptyList(),
)

/**
 * A landing/storage entry. Fields are TBD (blocked on later-phase screenshots), so this is deliberately a
 * flexible key/value shape; it will be replaced by strongly-typed fields once the storage-screen design
 * is confirmed.
 */
data class LandingStorageEntry(
    val id: String,
    val fields: Map<String, String> = emptyMap(),
)

/**
 * Root aggregate for an in-progress (or ready-to-submit) catch record. This is the single source of truth
 * persisted by [CatchRecordDraftRepository] and driven by the wizard flow (see `CatchRecordFlowViewModel`).
 */
data class CatchRecordDraft(
    val id: String,
    val vesselId: String,
    val isTripToday: Boolean? = null,
    val departureDate: DmyDate? = null,
    val returnDate: DmyDate? = null,
    val departurePort: PortSelection? = null,
    val returnPort: PortSelection? = null,
    val gearUses: List<GearUse> = emptyList(),
    val landingStorageEntries: List<LandingStorageEntry> = emptyList(),
    val status: DraftStatus = DraftStatus.Draft,
    val modifiedAtEpochMillis: Long,
)
