package uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft

/**
 * Status of a [CatchRecordDraft]. Only [Draft] and [ReadyToSubmit] are non-terminal ("active") — an
 * active draft is what [CatchRecordDraftRepository] enforces at most one of, per vessel (the confirmed
 * "draft singularity" assumption). [PendingSync], [Submitted] and [Discarded] are terminal and never
 * block starting a new draft for the same vessel.
 *
 * [PendingSync] (Phase 8): the user accepted the declaration on the check-your-answers screen while
 * offline (or the online submit attempt failed) — the record is treated as "committed" from the user's
 * point of view (they may start a new trip's draft immediately) even though the network submission is
 * still queued; see `CatchRecordSyncWorker`/ADR 0009. It transitions to [Submitted] once that background
 * sync succeeds.
 */
enum class DraftStatus {
    Draft,
    ReadyToSubmit,
    PendingSync,
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

/**
 * A species catch entry captured against a [GearUse] (Phase 5). Added via the gear-species search screen
 * (initially with every weight `null` and [confirmedCaught] `false`), then confirmed/edited on the
 * gear-species checklist screen: ticking the checklist checkbox sets [confirmedCaught] true and requires
 * [weightAboveMinimumSizeKg] (mandatory-ness varies per species — see
 * [uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species.weightAboveMinimumSizeMandatory]);
 * [weightBelowMinimumSizeKg]/[weightLegallyDiscardedKg] are always optional, progressively-disclosed
 * fields. Mirrors [GearUse.confirmedUsedOnTrip]'s "added but not yet confirmed" pattern: an
 * added-but-unchecked species stays in the list (its captured measurements are not lost) but is excluded
 * from the per-gear species step's "done" condition — see `nextGearUsePendingSpecies` in `WizardStep`.
 */
data class SpeciesWeightEntry(
    val id: String,
    val speciesId: String,
    val weightAboveMinimumSizeKg: Double? = null,
    val weightBelowMinimumSizeKg: Double? = null,
    val weightLegallyDiscardedKg: Double? = null,
    val confirmedCaught: Boolean = false,
)

/**
 * A species entry for the trip-level "not landing straight away" follow-up (Phase 5B, screen 4): the
 * species (deduplicated across every confirmed gear's [SpeciesWeightEntry.confirmedCaught] entries) and the
 * single weight kept onboard/in keep pots. Deliberately a much simpler shape than [SpeciesWeightEntry] — no
 * below-minimum/discarded sub-fields exist on this screen, per the confirmed screenshot.
 */
data class NotLandedSpeciesEntry(
    val speciesId: String,
    val weightAboveMinimumSizeKeptOnboardKg: Double? = null,
)

/**
 * One gear deployment within a trip: its gear type, gear-specific measurements (keyed by a
 * gear-type-defined field name, since exact fields per gear type are not yet confirmed), its own
 * statistical sub-rectangle selection, and its own species/weight entries.
 */
data class GearUse(
    val id: String,
    val gearTypeId: String,
    /**
     * The statistical sub-rectangle **code** (e.g. `"38E95"`) recorded for this gear use (Phase 4), `null`
     * until the per-gear "Where was the majority of your catch caught using {gear}?" step has been
     * completed for it — see `GearStatRectangleScreen`, driven only for gear uses where
     * [confirmedUsedOnTrip] is true (FR7/FR8). Deliberately a plain code string, not a foreign-key id into
     * [uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.StatisticalSubRectangle] — the
     * "Other" free-text search path accepts any correctly formatted code, including ones not present in the
     * local reference-data stub, since the full geographic grid is not locally enumerable.
     */
    val statisticalSubRectangleCode: String?,
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
    /**
     * Phase 5B: whether there is any catch from this trip that will not be landed straight away, asked
     * once after every confirmed gear's species/weights step is complete. `null` means not yet answered.
     */
    val notLandedStraightAway: Boolean? = null,
    /** Phase 5B: populated only when [notLandedStraightAway] is true — see [NotLandedSpeciesEntry]. */
    val notLandedSpeciesEntries: List<NotLandedSpeciesEntry> = emptyList(),
    val status: DraftStatus = DraftStatus.Draft,
    val modifiedAtEpochMillis: Long,
    /**
     * A stable, user-facing reference (e.g. `"A1234520260727150815"`), generated and persisted once by
     * [CatchRecordDraftRepository.startDraft] and shown throughout the wizard and on the Phase 8
     * submission-result screens. `null` only for drafts built directly in tests/previews that bypass the
     * repository; every draft created via the real app always has one.
     */
    val catchRecordReference: String? = null,
    /**
     * Whether the user has already seen and continued past the Phase 8 late-submission warning screen for
     * *this* draft state. Needed to break what would otherwise be an infinite loop in
     * [nextWizardStepForDraft]: the warning's own "Save and continue" action re-derives the next step from
     * the (otherwise unchanged) draft, and the time-based "is this late?" condition would still hold — see
     * `WizardStep.LateSubmissionWarning`.
     */
    val lateSubmissionWarningAcknowledged: Boolean = false,
)
