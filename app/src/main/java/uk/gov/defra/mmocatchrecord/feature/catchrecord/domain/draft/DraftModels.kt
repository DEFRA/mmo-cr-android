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
    /**
     * How many times this gear was shot/deployed on this specific trip. Currently modelled as universal
     * across every gear type — shown on the gear-summary checklist whenever [confirmedUsedOnTrip] is
     * ticked — because only Seine nets is confirmed by screenshot at this phase. This is a deliberate
     * placeholder assumption: a future phase may need to make it gear-type-conditional if some gear types
     * (e.g. static gear such as pots/traps) turn out not to have a "shots" concept.
     */
    val numberOfShots: Int? = null,
    /**
     * Whether the user has confirmed (via the gear-summary checklist's checkbox) that this gear was
     * actually used on **this** trip. A gear use can exist with its measurements already captured but
     * remain unconfirmed — e.g. added then left unticked — in which case it is excluded from the per-gear
     * FR7/FR8 downstream loop (stat-rectangle + species/weights, built in a later phase) while still being
     * retained here so its captured measurements are not lost.
     */
    val confirmedUsedOnTrip: Boolean = false,
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
