package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.NotLandedSpeciesEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelection
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelectionMode
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SpeciesWeightEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SubmissionMappingException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.UnresolvedGearMeasurementException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementField
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldKeys.MESH_SIZE_MM
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.SpeciesWeightPrecision
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.StatisticalSubRectangle
import java.util.Locale

private const val EXPECTED_REPLACE_JSON =
    """{"vessel":{"id":"vessel-1"},""" +
        """"trip":{"startedAndFinishedToday":false,""" +
        """"departurePort":{"id":"port-1","codeSnapshot":"GBHTG","nameSnapshot":"Hastings"},""" +
        """"returnPort":{"id":"port-1","codeSnapshot":"GBHTG","nameSnapshot":"Hastings"},""" +
        """"dateStarted":"2026-07-27","dateEnded":"2026-07-28"},""" +
        """"pairFishing":{"enabled":false,"pairVessel":null,"pairSkipperName":null},""" +
        """"gears":[{"gear":{"id":"gear-1","codeSnapshot":"DRB","nameSnapshot":"Seine net"},""" +
        """"statisticalArea":{"id":"rect-1","codeSnapshot":"38E95","nameSnapshot":"38E9"},""" +
        """"characteristics":[{"characteristicId":"char-1","nameSnapshot":"Mesh size","value":100.0,""" +
        """"unitSnapshot":"mm"}],""" +
        """"speciesCaught":[{"id":"species-1","faoCodeSnapshot":"MAC","nameSnapshot":"Atlantic mackerel",""" +
        """"weightAboveMinimumKg":120.5,"weightBelowMinimumKg":4.0,"weightPrecision":"oneDecimalPlace"}]}],""" +
        """"speciesNotLanded":[{"id":"species-1","faoCodeSnapshot":"MAC","nameSnapshot":"Atlantic mackerel",""" +
        """"weightAboveMinimumKg":2.5,"weightPrecision":"oneDecimalPlace"}]}"""

/** Mapper reproduces the backend example shape from an equivalent fixture draft (ADR 0026, Phase 3). */
class CatchRecordSubmissionPayloadMapperTests {
    private val json = Json { ignoreUnknownKeys = true }

    private val ports =
        listOf(Port(id = "port-1", name = "Hastings", statisticalAreaId = "area-1", code = "GBHTG"))
    private val gearTypes =
        listOf(
            GearType(
                id = "gear-1",
                name = "Seine net",
                measurementFields =
                    listOf(
                        GearMeasurementField(
                            key = MESH_SIZE_MM,
                            label = "Mesh size",
                            type = GearMeasurementFieldType.Decimal,
                            unit = "mm",
                            id = "char-1",
                        ),
                    ),
                code = "DRB",
            ),
        )
    private val species =
        listOf(
            Species(
                id = "species-1",
                name = "Atlantic mackerel",
                faoCode = "MAC",
                weightPrecision = SpeciesWeightPrecision.OneDecimalPlace,
            ),
        )
    private val rectangles =
        listOf(
            StatisticalSubRectangle(
                id = "rect-1",
                code = "38E95",
                statisticalAreaId = "area-1",
                icesRectangleName = "38E9",
            ),
        )

    private fun fixtureDraft(gearUses: List<GearUse> = listOf(confirmedGearUse())): CatchRecordDraft =
        CatchRecordDraft(
            id = "draft-1",
            vesselId = "vessel-1",
            isTripToday = false,
            departureDate = DmyDate(day = 27, month = 7, year = 2026),
            returnDate = DmyDate(day = 28, month = 7, year = 2026),
            departurePort = PortSelection("port-1", PortSelectionMode.FirstTime),
            returnPort = PortSelection("port-1", PortSelectionMode.FirstTime),
            gearUses = gearUses,
            notLandedSpeciesEntries =
                listOf(NotLandedSpeciesEntry("species-1", weightAboveMinimumSizeKeptOnboardKg = 2.5)),
            status = DraftStatus.ReadyToSubmit,
            modifiedAtEpochMillis = 0L,
        )

    private fun confirmedGearUse(): GearUse =
        GearUse(
            id = "gear-use-1",
            gearTypeId = "gear-1",
            statisticalSubRectangleCode = "38E95",
            measurements = mapOf(MESH_SIZE_MM to MeasurementValue.Numeric(100.0, "mm")),
            speciesWeights =
                listOf(
                    SpeciesWeightEntry(
                        id = "sw-1",
                        speciesId = "species-1",
                        weightAboveMinimumSizeKg = 120.5,
                        weightBelowMinimumSizeKg = 4.0,
                        confirmedCaught = true,
                    ),
                ),
            confirmedUsedOnTrip = true,
        )

    private fun replace(
        draft: CatchRecordDraft = fixtureDraft(),
        ports: List<Port> = this.ports,
        gearTypes: List<GearType> = this.gearTypes,
        species: List<Species> = this.species,
        rectangles: List<StatisticalSubRectangle> = this.rectangles,
    ) = CatchRecordSubmissionPayloadMapper.toReplacePayload(draft, ports, gearTypes, species, rectangles)

    @Test
    fun `toReplacePayload reproduces the backend example shape byte for byte`() {
        assertEquals(EXPECTED_REPLACE_JSON, json.encodeToString(replace()))
    }

    @Test
    fun `toReplacePayload excludes gear uses not confirmed used on trip`() {
        val unconfirmed = confirmedGearUse().copy(confirmedUsedOnTrip = false)

        val payload = replace(fixtureDraft(gearUses = listOf(unconfirmed)))

        assertEquals(emptyList<Any>(), payload.gears)
    }

    @Test
    fun `toReplacePayload excludes species not confirmed caught`() {
        val unconfirmedSpecies = SpeciesWeightEntry(id = "sw-2", speciesId = "species-1", confirmedCaught = false)
        val withUnconfirmedSpecies = confirmedGearUse().copy(speciesWeights = listOf(unconfirmedSpecies))

        val payload = replace(fixtureDraft(gearUses = listOf(withUnconfirmedSpecies)))

        assertEquals(emptyList<Any>(), payload.gears.single().speciesCaught)
    }

    @Test
    fun `an unresolvable port throws a terminal mapping exception`() {
        assertThrows(SubmissionMappingException::class.java) { replace(ports = emptyList()) }
    }

    @Test
    fun `an unresolvable gear type throws a terminal mapping exception`() {
        assertThrows(SubmissionMappingException::class.java) { replace(gearTypes = emptyList()) }
    }

    @Test
    fun `an unresolvable statistical sub-rectangle throws a terminal mapping exception`() {
        assertThrows(SubmissionMappingException::class.java) { replace(rectangles = emptyList()) }
    }

    @Test
    fun `an unresolvable species throws a terminal mapping exception`() {
        assertThrows(SubmissionMappingException::class.java) { replace(species = emptyList()) }
    }

    @Test
    fun `an unresolvable gear measurement key throws a terminal mapping exception`() {
        val gearUseWithUnknownMeasurement =
            confirmedGearUse().copy(measurements = mapOf("unknown_key" to MeasurementValue.Text("x")))

        assertThrows(SubmissionMappingException::class.java) {
            replace(fixtureDraft(gearUses = listOf(gearUseWithUnknownMeasurement)))
        }
    }

    @Test
    fun `a measurement field with no apiMeasurementId throws the transient unresolved variant`() {
        val staleField = gearTypes.single().measurementFields[0].copy(id = "")
        val staleGearTypes = listOf(gearTypes.single().copy(measurementFields = listOf(staleField)))

        assertThrows(UnresolvedGearMeasurementException::class.java) { replace(gearTypes = staleGearTypes) }
    }

    @Test
    fun `a blank reference-data code is treated the same as unresolvable`() {
        val blankCodePort = ports.map { it.copy(code = "") }

        assertThrows(SubmissionMappingException::class.java) { replace(ports = blankCodePort) }
    }

    @Test
    fun `startedAndFinishedToday omits both trip dates`() {
        val todayDraft = fixtureDraft().copy(isTripToday = true, departureDate = null, returnDate = null)

        val payload = replace(todayDraft)

        assertEquals(null, payload.trip.dateStarted)
        assertEquals(null, payload.trip.dateEnded)
    }

    @Test
    fun `toCreatePayload carries the vessel id and reference through`() {
        val payload = CatchRecordSubmissionPayloadMapper.toCreatePayload(fixtureDraft())

        assertEquals("vessel-1", payload.vesselId)
    }

    @Test
    fun `S8 dates always format with ASCII digits regardless of the device default locale`() {
        val originalDefault = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-SA"))

            val payload = replace()

            assertEquals("2026-07-27", payload.trip.dateStarted)
        } finally {
            Locale.setDefault(originalDefault)
        }
    }
}
