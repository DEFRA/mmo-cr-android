package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.submission

import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.NotLandedSpeciesEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SpeciesWeightEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Vessel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardStep
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.GearMeasurementSupport
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.species.SpeciesSupport

/** Which check-your-answers section a [CheckYourAnswersRow] belongs to — drives the section header shown. */
enum class CheckYourAnswersSectionKind {
    Trip,
    GearUsed,
    SpeciesCaught,
    SpeciesNotLanded,
}

/**
 * The kind of value shown on one check-your-answers row. Fixed/structural rows (dates, ports, vessel,
 * decision answers) resolve their label from a localized string resource keyed by this enum; [Measurement]
 * rows carry their own [CheckYourAnswersRow.dynamicLabel] instead, since gear-type measurement field labels
 * are plain, unlocalized reference data (see [GearMeasurementSupport]), not app string resources.
 */
enum class CheckYourAnswersFieldKind {
    Vessel,
    DepartureDate,
    ReturnDate,
    DeparturePort,
    ReturnPort,
    StatisticalSubArea,
    GearType,
    TimesShot,
    Measurement,
    Species,
    WeightAboveMinimumSize,
    WeightBelowMinimumSize,
    WeightLegallyDiscarded,
    NotLandedStraightAway,
    WeightKeptOnboard,
}

/**
 * One row of the check-your-answers screen: a label (resolved from [kind], or [dynamicLabel] for
 * [CheckYourAnswersFieldKind.Measurement] rows), its [value] (already display-formatted, e.g. "50 mm"), and
 * the [changeStep] its "Change" link deep-links back to. [changeGearUseId], when non-null, is the stable,
 * explicit gear-use edit context (finding: "Completed gear measurement/stat/species must be editable
 * through Change and back flows via stable explicit edit context IDs typed nav route args") carried as a
 * typed nav route argument so the destination screen edits that exact gear use in place rather than
 * ambiguously resolving "whichever gear is next pending" (which would resolve to nothing for an
 * already-complete gear) — see [uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.editRouteFor].
 */
data class CheckYourAnswersRow(
    val kind: CheckYourAnswersFieldKind,
    val value: String,
    val changeStep: WizardStep,
    val dynamicLabel: String? = null,
    val changeGearUseId: String? = null,
)

/**
 * One section of the check-your-answers screen: a [kind] (drives the section header shown) plus an
 * optional [heading] sub-title for sections repeated per gear/species (e.g. the gear type name, or
 * "{gear} – {species}"), and its [rows].
 */
data class CheckYourAnswersSection(
    val kind: CheckYourAnswersSectionKind,
    val rows: List<CheckYourAnswersRow>,
    val heading: String? = null,
)

/**
 * Pure (no Android/Compose dependency, directly unit-testable) builder for the Phase 8 check-your-answers
 * screen's section/row data — see `CheckYourAnswersScreen`.
 *
 * TODO(Phase 6/7): once landing-storage capture exists, add a `buildLandingStorageSections` here and splice
 * it into [buildSections] (after "Gear used", before "Species caught", to match the capture order) rather
 * than reworking this builder's shape.
 */
object CheckYourAnswersSupport {
    fun buildSections(
        draft: CatchRecordDraft,
        vessels: List<Vessel>,
        ports: List<Port>,
        gearTypes: List<GearType>,
        species: List<Species>,
    ): List<CheckYourAnswersSection> {
        val confirmedGearUses = draft.gearUses.filter { it.confirmedUsedOnTrip }
        val sections = mutableListOf<CheckYourAnswersSection>()
        sections += buildTripSection(draft, vessels, ports, confirmedGearUses)
        confirmedGearUses.forEach { gearUse -> sections += buildGearSection(gearUse, gearTypes) }
        confirmedGearUses.forEach { gearUse ->
            val gearName = displayGearNameFor(gearUse, gearTypes)
            val multipleGears = confirmedGearUses.size > 1
            gearUse.speciesWeights.filter { it.confirmedCaught }.forEach { entry ->
                sections += buildSpeciesSection(entry, species, gearName.takeIf { multipleGears }, gearUse.id)
            }
        }
        if (draft.notLandedStraightAway == true) {
            sections += buildNotLandedDecisionSection()
            draft.notLandedSpeciesEntries.forEach { entry ->
                sections += buildNotLandedSpeciesSection(entry, species)
            }
        }
        return sections
    }

    private fun buildTripSection(
        draft: CatchRecordDraft,
        vessels: List<Vessel>,
        ports: List<Port>,
        confirmedGearUses: List<GearUse>,
    ): CheckYourAnswersSection {
        val rows = mutableListOf<CheckYourAnswersRow>()
        val vesselName = vessels.firstOrNull { it.id == draft.vesselId }?.name ?: draft.vesselId
        rows += CheckYourAnswersRow(CheckYourAnswersFieldKind.Vessel, vesselName, WizardStep.VesselSelection)
        draft.departureDate?.let {
            rows +=
                CheckYourAnswersRow(CheckYourAnswersFieldKind.DepartureDate, formatDate(it), WizardStep.DepartureDate)
        }
        draft.returnDate?.let {
            rows += CheckYourAnswersRow(CheckYourAnswersFieldKind.ReturnDate, formatDate(it), WizardStep.ReturnDate)
        }
        draft.departurePort?.let { selection ->
            val name = ports.firstOrNull { it.id == selection.portId }?.name ?: selection.portId
            rows += CheckYourAnswersRow(CheckYourAnswersFieldKind.DeparturePort, name, WizardStep.DeparturePort)
        }
        draft.returnPort?.let { selection ->
            val name = ports.firstOrNull { it.id == selection.portId }?.name ?: selection.portId
            rows += CheckYourAnswersRow(CheckYourAnswersFieldKind.ReturnPort, name, WizardStep.ReturnPort)
        }
        // Ambiguity (flagged in the change summary, not silently guessed): the confirmed screenshot shows a
        // single trip-level "Statistical sub area" row, but statistical sub-rectangles are captured
        // per-gear (Phase 4), not once per trip. Resolved conservatively: show this row only when every
        // confirmed gear use shares exactly one distinct code (a safe "they all agree" case); omit it
        // entirely rather than inventing a merge rule when gear uses disagree or none are confirmed yet.
        val distinctCodes = confirmedGearUses.mapNotNull { it.statisticalSubRectangleCode }.distinct()
        distinctCodes.singleOrNull()?.let { code ->
            rows +=
                CheckYourAnswersRow(
                    CheckYourAnswersFieldKind.StatisticalSubArea,
                    code,
                    WizardStep.GearStatRectangle,
                )
        }
        return CheckYourAnswersSection(CheckYourAnswersSectionKind.Trip, rows)
    }

    private fun buildGearSection(
        gearUse: GearUse,
        gearTypes: List<GearType>,
    ): CheckYourAnswersSection {
        val gearType = gearTypes.firstOrNull { it.id == gearUse.gearTypeId }
        val rows = mutableListOf<CheckYourAnswersRow>()
        rows +=
            CheckYourAnswersRow(
                CheckYourAnswersFieldKind.GearType,
                displayGearNameFor(gearUse, gearTypes),
                WizardStep.GearSummary,
            )
        gearUse.numberOfShots?.let {
            rows += CheckYourAnswersRow(CheckYourAnswersFieldKind.TimesShot, it.toString(), WizardStep.GearSummary)
        }
        gearType?.measurementFields?.forEach { field ->
            val value = gearUse.measurements[field.key] ?: return@forEach
            rows +=
                CheckYourAnswersRow(
                    kind = CheckYourAnswersFieldKind.Measurement,
                    value = formatMeasurementValue(value),
                    changeStep = WizardStep.GearMeasurement,
                    dynamicLabel = field.label,
                    changeGearUseId = gearUse.id,
                )
        }
        return CheckYourAnswersSection(
            kind = CheckYourAnswersSectionKind.GearUsed,
            rows = rows,
            heading = displayGearNameFor(gearUse, gearTypes),
        )
    }

    private fun buildSpeciesSection(
        entry: SpeciesWeightEntry,
        species: List<Species>,
        gearNamePrefix: String?,
        gearUseId: String,
    ): CheckYourAnswersSection {
        val speciesName =
            species.firstOrNull { it.id == entry.speciesId }?.let(SpeciesSupport::displayNameFor)
                ?: entry.speciesId
        val rows = mutableListOf<CheckYourAnswersRow>()
        rows +=
            CheckYourAnswersRow(
                CheckYourAnswersFieldKind.Species,
                speciesName,
                WizardStep.GearSpeciesChecklist,
                changeGearUseId = gearUseId,
            )
        entry.weightAboveMinimumSizeKg?.let {
            rows +=
                CheckYourAnswersRow(
                    CheckYourAnswersFieldKind.WeightAboveMinimumSize,
                    formatWeight(it),
                    WizardStep.GearSpeciesChecklist,
                    changeGearUseId = gearUseId,
                )
        }
        entry.weightBelowMinimumSizeKg?.let {
            rows +=
                CheckYourAnswersRow(
                    CheckYourAnswersFieldKind.WeightBelowMinimumSize,
                    formatWeight(it),
                    WizardStep.GearSpeciesChecklist,
                    changeGearUseId = gearUseId,
                )
        }
        entry.weightLegallyDiscardedKg?.let {
            rows +=
                CheckYourAnswersRow(
                    CheckYourAnswersFieldKind.WeightLegallyDiscarded,
                    formatWeight(it),
                    WizardStep.GearSpeciesChecklist,
                    changeGearUseId = gearUseId,
                )
        }
        val heading = gearNamePrefix?.let { "$it – $speciesName" }
        return CheckYourAnswersSection(CheckYourAnswersSectionKind.SpeciesCaught, rows, heading)
    }

    private fun buildNotLandedDecisionSection(): CheckYourAnswersSection =
        CheckYourAnswersSection(
            kind = CheckYourAnswersSectionKind.SpeciesNotLanded,
            rows =
                listOf(
                    CheckYourAnswersRow(
                        CheckYourAnswersFieldKind.NotLandedStraightAway,
                        // Value intentionally left blank: this row's kind alone (always "Yes" — this
                        // section only exists when notLandedStraightAway == true) is enough for the
                        // Compose layer to render the localized "Yes" text without hardcoding English here.
                        "",
                        WizardStep.NotLandedStraightAwayDecision,
                    ),
                ),
        )

    private fun buildNotLandedSpeciesSection(
        entry: NotLandedSpeciesEntry,
        species: List<Species>,
    ): CheckYourAnswersSection {
        val speciesName =
            species.firstOrNull { it.id == entry.speciesId }?.let(SpeciesSupport::displayNameFor)
                ?: entry.speciesId
        val rows = mutableListOf<CheckYourAnswersRow>()
        rows +=
            CheckYourAnswersRow(
                CheckYourAnswersFieldKind.Species,
                speciesName,
                WizardStep.NotLandedStraightAwaySpecies,
            )
        entry.weightAboveMinimumSizeKeptOnboardKg?.let {
            rows +=
                CheckYourAnswersRow(
                    CheckYourAnswersFieldKind.WeightKeptOnboard,
                    formatWeight(it),
                    WizardStep.NotLandedStraightAwaySpecies,
                )
        }
        // No `heading` here (unlike buildSpeciesSection): this section never needs a gear-name-prefixed
        // sub-heading (not-landed species aren't captured per-gear), so a heading would only ever
        // duplicate the "Species" row above verbatim — the row (with its own "Change" link) is
        // authoritative.
        return CheckYourAnswersSection(CheckYourAnswersSectionKind.SpeciesNotLanded, rows)
    }

    private fun displayGearNameFor(
        gearUse: GearUse,
        gearTypes: List<GearType>,
    ): String {
        val gearType = gearTypes.firstOrNull { it.id == gearUse.gearTypeId }
        return gearType?.let(GearMeasurementSupport::displayNameFor) ?: gearUse.gearTypeId
    }

    private fun formatMeasurementValue(value: MeasurementValue): String =
        when (value) {
            is MeasurementValue.Numeric ->
                if (value.unit.isNotBlank()) {
                    "${GearMeasurementSupport.formatNumber(value.value)} ${value.unit}"
                } else {
                    GearMeasurementSupport.formatNumber(value.value)
                }
            is MeasurementValue.Text -> value.value
        }

    private fun formatWeight(kg: Double): String = "${GearMeasurementSupport.formatNumber(kg)} kg"

    private fun formatDate(date: DmyDate): String = "%02d/%02d/%04d".format(date.day, date.month, date.year)
}
