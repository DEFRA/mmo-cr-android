package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.submission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.NotLandedSpeciesEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelection
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelectionMode
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SpeciesWeightEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementField
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Vessel

class CheckYourAnswersSupportTests {
    private val vessel = Vessel(id = "vessel-1", name = "The Providence")
    private val departurePort = Port(id = "port-1", name = "Newlyn", statisticalAreaId = "area-1")
    private val returnPort = Port(id = "port-2", name = "Plymouth", statisticalAreaId = "area-1")
    private val seineNet =
        GearType(
            id = "gear-seine",
            name = "Seine nets",
            measurementFields =
                listOf(
                    GearMeasurementField(
                        key = "mesh_size_mm",
                        label = "Mesh size",
                        type = GearMeasurementFieldType.Integer,
                        unit = "mm",
                    ),
                ),
        )
    private val cod = Species(id = "species-cod", name = "Cod", faoCode = "COD")
    private val plaice = Species(id = "species-plaice", name = "Plaice", faoCode = "PLE")

    private fun draft(
        gearUses: List<GearUse> = emptyList(),
        notLandedStraightAway: Boolean? = null,
        notLandedSpeciesEntries: List<NotLandedSpeciesEntry> = emptyList(),
    ) = CatchRecordDraft(
        id = "draft-1",
        vesselId = vessel.id,
        isTripToday = false,
        departureDate = DmyDate(1, 6, 2024),
        returnDate = DmyDate(3, 6, 2024),
        departurePort = PortSelection(departurePort.id, PortSelectionMode.FirstTime),
        returnPort = PortSelection(returnPort.id, PortSelectionMode.FirstTime),
        gearUses = gearUses,
        notLandedStraightAway = notLandedStraightAway,
        notLandedSpeciesEntries = notLandedSpeciesEntries,
        modifiedAtEpochMillis = 0L,
    )

    private fun buildSections(theDraft: CatchRecordDraft) =
        CheckYourAnswersSupport.buildSections(
            draft = theDraft,
            vessels = listOf(vessel),
            ports = listOf(departurePort, returnPort),
            gearTypes = listOf(seineNet),
            species = listOf(cod, plaice),
        )

    @Test
    fun `trip section resolves vessel and port display names`() {
        val gearUse =
            GearUse(
                id = "gear-use-1",
                gearTypeId = seineNet.id,
                statisticalSubRectangleCode = "38E9",
                confirmedUsedOnTrip = true,
            )
        val sections = buildSections(draft(gearUses = listOf(gearUse)))
        val tripSection = sections.first { it.kind == CheckYourAnswersSectionKind.Trip }

        val vesselRow = tripSection.rows.first { it.kind == CheckYourAnswersFieldKind.Vessel }
        assertEquals("The Providence", vesselRow.value)
        val departurePortRow = tripSection.rows.first { it.kind == CheckYourAnswersFieldKind.DeparturePort }
        assertEquals("Newlyn", departurePortRow.value)
        val returnPortRow = tripSection.rows.first { it.kind == CheckYourAnswersFieldKind.ReturnPort }
        assertEquals("Plymouth", returnPortRow.value)
    }

    @Test
    fun `statistical sub area is shown when every confirmed gear shares one code`() {
        val gearOne =
            GearUse(
                id = "gear-use-1",
                gearTypeId = seineNet.id,
                statisticalSubRectangleCode = "38E9",
                confirmedUsedOnTrip = true,
            )
        val gearTwo =
            GearUse(
                id = "gear-use-2",
                gearTypeId = seineNet.id,
                statisticalSubRectangleCode = "38E9",
                confirmedUsedOnTrip = true,
            )
        val sections = buildSections(draft(gearUses = listOf(gearOne, gearTwo)))
        val tripSection = sections.first { it.kind == CheckYourAnswersSectionKind.Trip }

        val statAreaRow = tripSection.rows.firstOrNull { it.kind == CheckYourAnswersFieldKind.StatisticalSubArea }
        assertEquals("38E9", statAreaRow?.value)
    }

    @Test
    fun `statistical sub area is omitted when confirmed gear uses disagree`() {
        val gearOne =
            GearUse(
                id = "gear-use-1",
                gearTypeId = seineNet.id,
                statisticalSubRectangleCode = "38E9",
                confirmedUsedOnTrip = true,
            )
        val gearTwo =
            GearUse(
                id = "gear-use-2",
                gearTypeId = seineNet.id,
                statisticalSubRectangleCode = "39F0",
                confirmedUsedOnTrip = true,
            )
        val sections = buildSections(draft(gearUses = listOf(gearOne, gearTwo)))
        val tripSection = sections.first { it.kind == CheckYourAnswersSectionKind.Trip }

        assertTrue(tripSection.rows.none { it.kind == CheckYourAnswersFieldKind.StatisticalSubArea })
    }

    @Test
    fun `statistical sub area is omitted when there are no confirmed gear uses`() {
        val sections = buildSections(draft(gearUses = emptyList()))
        val tripSection = sections.first { it.kind == CheckYourAnswersSectionKind.Trip }
        assertTrue(tripSection.rows.none { it.kind == CheckYourAnswersFieldKind.StatisticalSubArea })
    }

    @Test
    fun `gear section includes times shot and formatted measurement rows`() {
        val gearOne =
            GearUse(
                id = "gear-use-1",
                gearTypeId = seineNet.id,
                statisticalSubRectangleCode = "38E9",
                confirmedUsedOnTrip = true,
                numberOfShots = 4,
                measurements = mapOf("mesh_size_mm" to MeasurementValue.Numeric(value = 50.0, unit = "mm")),
            )
        val sections = buildSections(draft(gearUses = listOf(gearOne)))
        val gearSection = sections.first { it.kind == CheckYourAnswersSectionKind.GearUsed }

        val timesShotRow = gearSection.rows.first { it.kind == CheckYourAnswersFieldKind.TimesShot }
        assertEquals("4", timesShotRow.value)
        val measurementRow = gearSection.rows.first { it.kind == CheckYourAnswersFieldKind.Measurement }
        assertEquals("Mesh size", measurementRow.dynamicLabel)
        assertEquals("50 mm", measurementRow.value)
    }

    @Test
    fun `species-caught section has no heading (redundant with the Species row) for a single confirmed gear`() {
        val gearOne =
            GearUse(
                id = "gear-use-1",
                gearTypeId = seineNet.id,
                statisticalSubRectangleCode = "38E9",
                confirmedUsedOnTrip = true,
                speciesWeights =
                    listOf(
                        SpeciesWeightEntry(
                            id = "sw-1",
                            speciesId = cod.id,
                            weightAboveMinimumSizeKg = 12.5,
                            confirmedCaught = true,
                        ),
                    ),
            )
        val sections = buildSections(draft(gearUses = listOf(gearOne)))
        val speciesSection = sections.first { it.kind == CheckYourAnswersSectionKind.SpeciesCaught }

        assertNull(speciesSection.heading)
        val speciesRow = speciesSection.rows.first { it.kind == CheckYourAnswersFieldKind.Species }
        assertEquals("Cod", speciesRow.value)
        val weightRow = speciesSection.rows.first { it.kind == CheckYourAnswersFieldKind.WeightAboveMinimumSize }
        assertEquals("12.5 kg", weightRow.value)
    }

    @Test
    fun `species-caught section heading is prefixed with the gear name when multiple gears are confirmed`() {
        val gearOne =
            GearUse(
                id = "gear-use-1",
                gearTypeId = seineNet.id,
                statisticalSubRectangleCode = "38E9",
                confirmedUsedOnTrip = true,
                speciesWeights =
                    listOf(SpeciesWeightEntry(id = "sw-1", speciesId = cod.id, confirmedCaught = true)),
            )
        val gearTwo =
            GearUse(
                id = "gear-use-2",
                gearTypeId = seineNet.id,
                statisticalSubRectangleCode = "39F0",
                confirmedUsedOnTrip = true,
                speciesWeights =
                    listOf(SpeciesWeightEntry(id = "sw-2", speciesId = plaice.id, confirmedCaught = true)),
            )
        val sections = buildSections(draft(gearUses = listOf(gearOne, gearTwo)))
        val speciesSections = sections.filter { it.kind == CheckYourAnswersSectionKind.SpeciesCaught }

        assertTrue(speciesSections.any { it.heading == "Seine nets – Cod" })
        assertTrue(speciesSections.any { it.heading == "Seine nets – Plaice" })
    }

    @Test
    fun `unconfirmed species-weight entries are excluded from species-caught sections`() {
        val gearOne =
            GearUse(
                id = "gear-use-1",
                gearTypeId = seineNet.id,
                statisticalSubRectangleCode = "38E9",
                confirmedUsedOnTrip = true,
                speciesWeights =
                    listOf(SpeciesWeightEntry(id = "sw-1", speciesId = cod.id, confirmedCaught = false)),
            )
        val sections = buildSections(draft(gearUses = listOf(gearOne)))
        assertTrue(sections.none { it.kind == CheckYourAnswersSectionKind.SpeciesCaught })
    }

    @Test
    fun `species-not-landed sections are omitted when notLandedStraightAway is not true`() {
        val sections = buildSections(draft(notLandedStraightAway = false))
        assertTrue(sections.none { it.kind == CheckYourAnswersSectionKind.SpeciesNotLanded })
    }

    @Test
    fun `species-not-landed sections include the decision row and one row per species, with no heading`() {
        val sections =
            buildSections(
                draft(
                    notLandedStraightAway = true,
                    notLandedSpeciesEntries =
                        listOf(NotLandedSpeciesEntry(speciesId = cod.id, weightAboveMinimumSizeKeptOnboardKg = 3.0)),
                ),
            )
        val notLandedSections = sections.filter { it.kind == CheckYourAnswersSectionKind.SpeciesNotLanded }
        assertEquals(2, notLandedSections.size)

        val decisionSection = notLandedSections.first()
        val decisionRow = decisionSection.rows.single()
        assertEquals(CheckYourAnswersFieldKind.NotLandedStraightAway, decisionRow.kind)

        val speciesSection = notLandedSections[1]
        assertNull(speciesSection.heading)
        val speciesRow = speciesSection.rows.first { it.kind == CheckYourAnswersFieldKind.Species }
        assertEquals("Cod", speciesRow.value)
        val weightRow = speciesSection.rows.first { it.kind == CheckYourAnswersFieldKind.WeightKeptOnboard }
        assertEquals("3 kg", weightRow.value)
    }

    @Test
    fun `unknown reference ids fall back to the raw id rather than crashing`() {
        val gearOne =
            GearUse(
                id = "gear-use-1",
                gearTypeId = "unknown-gear",
                statisticalSubRectangleCode = null,
                confirmedUsedOnTrip = true,
            )
        val sections = buildSections(draft(gearUses = listOf(gearOne)))
        val gearSection = sections.first { it.kind == CheckYourAnswersSectionKind.GearUsed }
        val gearTypeRow = gearSection.rows.first { it.kind == CheckYourAnswersFieldKind.GearType }
        assertEquals("unknown-gear", gearTypeRow.value)
        assertNull(gearSection.rows.firstOrNull { it.kind == CheckYourAnswersFieldKind.Measurement })
    }
}
