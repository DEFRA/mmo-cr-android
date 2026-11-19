package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val json = Json { ignoreUnknownKeys = true }

private const val PORT = """{"id":"p1","codeSnapshot":"GBHTG","nameSnapshot":"HASTINGS"}"""

private const val FULL_RETRIEVE_BODY =
    """{"schemaVersion":1,"id":"cr-1","catchRecordReference":"GBR-B14974-270726-150815",""" +
        """"ownerUserId":"user-1","status":"SUBMITTED","version":3,"numberOfSubmissions":1,""" +
        """"hasUnsubmittedChanges":false,""" +
        """"vessel":{"id":"v1","rssSnapshot":"RSS1","nameSnapshot":"ACHILLES","externalMarkSnapshot":"PH101",""" +
        """"lengthOverallMetresSnapshot":9.5},""" +
        """"trip":{"startedAndFinishedToday":false,"dateStarted":"2026-07-26","dateEnded":"2026-07-27",""" +
        """"departurePort":$PORT,"returnPort":$PORT},""" +
        """"pairFishing":{"enabled":true,"pairVessel":"PARTNER","pairSkipperName":"Skipper"},""" +
        """"gears":[{"associationId":"a1","gear":{"id":"g1","codeSnapshot":"OTB","nameSnapshot":"Otter trawl"},""" +
        """"characteristics":[{"characteristicId":"c1","nameSnapshot":"Mesh","value":80,"unitSnapshot":"mm"}],""" +
        """"statisticalArea":{"id":"s1","codeSnapshot":"31F1","nameSnapshot":"31F1"},""" +
        """"speciesCaught":[{"id":"sp1","faoCodeSnapshot":"COD","nameSnapshot":"Cod",""" +
        """"weightAboveMinimumKg":10.5,"weightBelowMinimumKg":1.0,"weightLegallyDiscardedKg":0.5,""" +
        """"weightPrecision":"ESTIMATED"}]}],""" +
        """"speciesNotLanded":[],"artifacts":[{"submissionNumber":1,"type":"JSON_SNAPSHOT"}],""" +
        """"createdAt":"c-at","createdBy":"c-by","updatedAt":"u-at","updatedBy":"u-by",""" +
        """"submittedAt":"s-at","submittedBy":"s-by","completedAt":null,"completedBy":null,""" +
        """"displayStatus":"Submitted","sectionCompletion":{"trip":true,"pairFishing":true,"gears":true},""" +
        """"completedSections":["trip","pairFishing","gears"],"incompleteSections":[],""" +
        """"progress":{"hasUnsubmittedChanges":false,"numberOfSubmissions":1,""" +
        """"incompleteGearAssociationIds":[],"currentIncompleteGearAssociationId":null,""" +
        """"allGearsComplete":true},"submissionEligible":true}"""

/** Read-side and error DTOs decode every field the backend sends (ADR 0026). */
class CatchRecordSubmissionDtoParsingTests {
    @Test
    fun `the full canonical retrieve response decodes every field`() {
        val dto = json.decodeFromString(RetrieveCatchRecordResponseDto.serializer(), FULL_RETRIEVE_BODY)

        assertEquals(1, dto.schemaVersion)
        assertEquals("cr-1", dto.id)
        assertEquals("GBR-B14974-270726-150815", dto.catchRecordReference)
        assertEquals("user-1", dto.ownerUserId)
        assertEquals("SUBMITTED", dto.status)
        assertEquals(3, dto.version)
        assertEquals(1, dto.numberOfSubmissions)
        assertFalse(dto.hasUnsubmittedChanges)
        assertEquals(
            listOf("c-at", "c-by", "u-at", "u-by"),
            listOf(dto.createdAt, dto.createdBy, dto.updatedAt, dto.updatedBy),
        )
        assertEquals("s-at", dto.submittedAt)
        assertEquals("s-by", dto.submittedBy)
        assertNull(dto.completedAt)
        assertNull(dto.completedBy)
        assertEquals("Submitted", dto.displayStatus)
        assertTrue(dto.sectionCompletion.gears)
        assertEquals(3, dto.completedSections.size)
        assertTrue(dto.incompleteSections.isEmpty())
        assertTrue(dto.progress.allGearsComplete)
        assertTrue(dto.submissionEligible)
        assertTrue(dto.speciesNotLanded.isEmpty())
        assertEquals("JSON_SNAPSHOT", dto.artifacts.single().type)
        assertTrue(dto.pairFishing.enabled)
    }

    @Test
    fun `the vessel and trip snapshots decode every field`() {
        val dto = json.decodeFromString(RetrieveCatchRecordResponseDto.serializer(), FULL_RETRIEVE_BODY)

        with(dto.vessel) {
            assertEquals("v1", id)
            assertEquals("RSS1", rssSnapshot)
            assertEquals("ACHILLES", nameSnapshot)
            assertEquals("PH101", externalMarkSnapshot)
            assertEquals(9.5, lengthOverallMetresSnapshot)
        }
        with(dto.trip) {
            assertFalse(startedAndFinishedToday)
            assertEquals("2026-07-26", dateStarted)
            assertEquals("2026-07-27", dateEnded)
            assertEquals("GBHTG", departurePort.codeSnapshot)
            assertEquals("HASTINGS", returnPort.nameSnapshot)
        }
    }

    @Test
    fun `the gear association snapshot decodes every field`() {
        val gear = json.decodeFromString(RetrieveCatchRecordResponseDto.serializer(), FULL_RETRIEVE_BODY).gears[0]

        assertEquals("a1", gear.associationId)
        assertEquals("OTB", gear.gear.codeSnapshot)
        assertEquals("31F1", gear.statisticalArea?.codeSnapshot)
        val characteristic = gear.characteristics.single()
        assertEquals(JsonPrimitive(80), characteristic.value)
        assertEquals("mm", characteristic.unitSnapshot)
        val species = gear.speciesCaught.single()
        assertEquals(10.5, species.weightAboveMinimumKg)
        assertEquals(1.0, species.weightBelowMinimumKg)
        assertEquals(0.5, species.weightLegallyDiscardedKg)
        assertEquals("ESTIMATED", species.weightPrecision)
    }

    @Test
    fun `optional snapshot fields default to null when absent`() {
        val vessel =
            json.decodeFromString(
                VesselSnapshotDto.serializer(),
                """{"id":"v1","rssSnapshot":"R","nameSnapshot":"N","externalMarkSnapshot":"E"}""",
            )
        val gear =
            json.decodeFromString(
                GearAssociationSnapshotDto.serializer(),
                """{"associationId":"a1","gear":{"id":"g1","codeSnapshot":"C","nameSnapshot":"N"},""" +
                    """"characteristics":[],"speciesCaught":[]}""",
            )
        val recovery =
            json.decodeFromString(
                CatchRecordRecoverySnapshotDto.serializer(),
                """{"id":"cr-1","status":"DRAFT","version":1,"hasUnsubmittedChanges":true}""",
            )

        assertNull(vessel.lengthOverallMetresSnapshot)
        assertNull(gear.statisticalArea)
        assertNull(recovery.catchRecordReference)
    }

    @Test
    fun `write-side DTOs omit their optional fields by default`() {
        val characteristic = CharacteristicDto("c1", "Mesh", JsonPrimitive(80))
        val species = SpeciesWeightEntryDto("sp1", "COD", "Cod")
        val gear =
            GearAssociationDto(
                gear = GearRefDto("g1", "OTB", "Otter trawl"),
                statisticalArea = StatisticalAreaDto("s1", "31F1", "31F1"),
                characteristics = listOf(characteristic),
                speciesCaught = listOf(species),
            )

        assertNull(characteristic.unitSnapshot)
        assertNull(species.weightAboveMinimumKg)
        assertNull(species.weightBelowMinimumKg)
        assertNull(species.weightLegallyDiscardedKg)
        assertNull(species.weightPrecision)
        assertNull(gear.associationId)
    }

    @Test
    fun `read-side DTOs constructed in code default their optional fields to null`() {
        val port = PortRefDto("p1", "GBHTG", "HASTINGS")
        val vessel = VesselSnapshotDto("v1", "RSS1", "ACHILLES", "PH101")
        val gear =
            GearAssociationSnapshotDto("a1", GearRefDto("g1", "OTB", "Otter"), emptyList(), speciesCaught = emptyList())
        val recovery =
            CatchRecordRecoverySnapshotDto(id = "cr-1", status = "DRAFT", version = 1, hasUnsubmittedChanges = true)
        val progress = ProgressDto(true, 0, emptyList(), null, false)
        val retrieve =
            RetrieveCatchRecordResponseDto(
                schemaVersion = 1,
                id = "cr-1",
                catchRecordReference = "ref",
                ownerUserId = "user-1",
                status = "DRAFT",
                version = 1,
                numberOfSubmissions = 0,
                hasUnsubmittedChanges = true,
                vessel = vessel,
                trip = TripSnapshotDto(true, "2026-07-27", "2026-07-27", port, port),
                pairFishing = PairFishingDto(enabled = false, pairVessel = null, pairSkipperName = null),
                gears = listOf(gear),
                speciesNotLanded = emptyList(),
                artifacts = emptyList(),
                createdAt = "c",
                createdBy = "c",
                updatedAt = "u",
                updatedBy = "u",
                submittedAt = null,
                submittedBy = null,
                completedAt = null,
                completedBy = null,
                displayStatus = "Draft",
                sectionCompletion = SectionCompletionDto(trip = true, pairFishing = true, gears = false),
                completedSections = emptyList(),
                incompleteSections = emptyList(),
                progress = progress,
                submissionEligible = false,
            )

        assertNull(vessel.lengthOverallMetresSnapshot)
        assertNull(gear.statisticalArea)
        assertNull(recovery.catchRecordReference)
        assertNull(ErrorResponseDto(500, "X", "m").details)
        assertNull(ErrorDetailDto().path)
        assertEquals(retrieve, retrieve.copy())
        assertEquals("2026-07-27", retrieve.trip.dateEnded)
    }

    @Test
    fun `an error response decodes its details`() {
        val dto =
            json.decodeFromString(
                ErrorResponseDto.serializer(),
                """{"statusCode":422,"code":"VALIDATION","message":"bad","correlationId":"corr-1",""" +
                    """"details":[{"path":"trip.dateStarted","code":"RANGE","message":"too old","min":0,""" +
                    """"max":365,"format":"date","allowedValues":["A",1]}]}""",
            )

        assertEquals(422, dto.statusCode)
        assertEquals("VALIDATION", dto.code)
        assertEquals("bad", dto.message)
        assertEquals("corr-1", dto.correlationId)
        val detail = dto.details.orEmpty().single()
        assertEquals("trip.dateStarted", detail.path)
        assertEquals("RANGE", detail.code)
        assertEquals("too old", detail.message)
        assertEquals(0.0, detail.min)
        assertEquals(365.0, detail.max)
        assertEquals("date", detail.format)
        assertEquals(listOf(JsonPrimitive("A"), JsonPrimitive(1)), detail.allowedValues)
    }

    @Test
    fun `a minimal error response leaves optional fields null`() {
        val dto =
            json.decodeFromString(
                ErrorResponseDto.serializer(),
                """{"statusCode":500,"code":"X","message":"m"}""",
            )
        val detail = json.decodeFromString(ErrorDetailDto.serializer(), "{}")

        assertNull(dto.correlationId)
        assertNull(dto.details)
        assertNull(detail.path)
        assertNull(detail.code)
        assertNull(detail.message)
        assertNull(detail.min)
        assertNull(detail.max)
        assertNull(detail.format)
        assertNull(detail.allowedValues)
    }
}
