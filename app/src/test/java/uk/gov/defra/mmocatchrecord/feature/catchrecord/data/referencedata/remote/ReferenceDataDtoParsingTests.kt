package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val json = Json { ignoreUnknownKeys = true }

/** DTO parsing from realistic fixtures shaped exactly like the live-verified DEV contract (ADR 0015). */
class ReferenceDataDtoParsingTests {
    @Test
    fun `vessel envelope parses and ignores unknown fields`() {
        val envelope =
            json.decodeFromString<ReferenceDataEnvelopeDto<VesselMobileDto>>(
                """
                {"dataset":"vessels","collectionId":"col-1","schemaVersion":"1.0","version":"v1",
                "view":"mobile","total":1,"unexpectedField":"ignored","items":[
                {"id":"00000000-0000-4000-8000-000000000001","name":"ACHILLES","pln":"PH101",
                "cfr":"GBR0000001","displayName":"ACHILLES PH101","lengthOverallMetres":8.74}]}
                """.trimIndent(),
            )

        assertEquals(1, envelope.items.size)
        val vessel = envelope.items.single()
        assertEquals("ACHILLES PH101", vessel.displayName)
        assertEquals(8.74, vessel.lengthOverallMetres)
    }

    @Test
    fun `port with null coordinate parses`() {
        val envelope =
            json.decodeFromString<ReferenceDataEnvelopeDto<PortMobileDto>>(
                """
                {"dataset":"ports","collectionId":"col-2","schemaVersion":"1.0","version":"v1",
                "view":"mobile","total":1,"items":[
                {"id":"49e319b2-0000-0000-0000-000000000002","code":"GBAOT","name":"Abbotsbury",
                "displayName":"Abbotsbury","coordinate":null}]}
                """.trimIndent(),
            )

        assertNull(envelope.items.single().coordinate)
    }

    @Test
    fun `port with a coordinate parses`() {
        val envelope =
            json.decodeFromString<ReferenceDataEnvelopeDto<PortMobileDto>>(
                """
                {"dataset":"ports","collectionId":"col-2","schemaVersion":"1.0","version":"v1",
                "view":"mobile","total":1,"items":[
                {"id":"49e319b2-0000-0000-0000-000000000002","code":"GBAOT","name":"Abbotsbury",
                "displayName":"Abbotsbury","coordinate":{"latitude":50.66,"longitude":-2.59}}]}
                """.trimIndent(),
            )

        val coordinate = envelope.items.single().coordinate
        assertEquals(50.66, coordinate?.latitude)
        assertEquals(-2.59, coordinate?.longitude)
    }

    @Test
    fun `species envelope parses`() {
        val envelope =
            json.decodeFromString<ReferenceDataEnvelopeDto<SpeciesMobileDto>>(
                """
                {"dataset":"species","collectionId":"col-3","schemaVersion":"1.0","version":"v1",
                "view":"mobile","total":1,"items":[
                {"id":"416A8BFB-0000-0000-0000-000000000003","faoCode":"STU",
                "scientificName":"Acipenseridae","displayName":"Sturgeons nei"}]}
                """.trimIndent(),
            )

        assertEquals("STU", envelope.items.single().faoCode)
    }

    @Test
    fun `gears envelope with context parses measurements and band`() {
        val envelope =
            json.decodeFromString<GearsEnvelopeDto>(
                """
                {"dataset":"gears","collectionId":"col-4","schemaVersion":"1.0","version":"v1",
                "view":"mobile","total":1,
                "context":{"vesselLengthBand":"under-10m"},
                "measurements":[
                {"id":"ec4b5815-0000-0000-0000-000000000005","code":"GM","label":"Long label text",
                "kind":"number","unit":"m","minimumValue":null,"maximumValue":null}],
                "items":[
                {"id":"F3C85F59-0000-0000-0000-000000000004","code":"TBB","name":"Beam trawl",
                "category":{"id":"c1","code":"TRAWL_NETS","name":"Trawl nets"},"pairFishing":false,
                "requiredMeasurementIds":[],"variableMeasurementIds":["ec4b5815-0000-0000-0000-000000000005"]}]}
                """.trimIndent(),
            )

        assertEquals("under-10m", envelope.context?.vesselLengthBand)
        assertEquals(1, envelope.measurements.size)
        assertEquals("TBB", envelope.items.single().code)
    }

    @Test
    fun `gears envelope without context parses`() {
        val envelope =
            json.decodeFromString<GearsEnvelopeDto>(
                """
                {"dataset":"gears","collectionId":"col-4","schemaVersion":"1.0","version":"v1",
                "view":"mobile","total":1,"measurements":[],"items":[
                {"id":"F3C85F59-0000-0000-0000-000000000004","code":"TBB","name":"Beam trawl",
                "category":{"id":"c1","code":"TRAWL_NETS","name":"Trawl nets"},"pairFishing":false,
                "requiredMeasurementIds":[],"variableMeasurementIds":[]}]}
                """.trimIndent(),
            )

        assertNull(envelope.context)
    }

    @Test
    fun `manifest with map datasets is parsed without breaking`() {
        val manifest =
            json.decodeFromString<ManifestDto>(
                """
                {"manifestId":"m-1","version":"2026-01-01T00:00:00Z","generatedAt":"2026-01-01T00:00:00Z",
                "datasets":[
                {"dataset":"vessels","collectionId":"col-1","schemaVersion":"1.0","version":"v1",
                "etag":"61dd8fed9ed7cc2a4a96a97da8a1174c","url":"/api/v1/reference-data/vessels",
                "format":"json","itemCount":30},
                {"dataset":"map-land","collectionId":"col-9","schemaVersion":"1.0","version":"v1",
                "etag":"abc","url":"/api/v1/reference-data/map/land","format":"geojson","itemCount":5}]}
                """.trimIndent(),
            )

        assertEquals(2, manifest.datasets.size)
        val recognised = manifest.datasets.filter { it.dataset in setOf("vessels", "ports", "species", "gears") }
        assertEquals(1, recognised.size)
    }

    @Test
    fun `error envelope parses optional fields`() {
        val envelope =
            json.decodeFromString<ErrorEnvelopeDto>(
                """
                {"error":{"code":"invalid_request","message":"bad input","traceId":"trace-1",
                "dataset":"vessels","retryable":false,
                "details":[{"path":"limit","message":"must be positive"}]}}
                """.trimIndent(),
            )

        assertEquals("invalid_request", envelope.error.code)
        assertTrue(envelope.error.retryable == false)
    }

    @Test
    fun `error envelope parses with only required fields`() {
        val envelope =
            json.decodeFromString<ErrorEnvelopeDto>("""{"error":{"code":"internal_error","message":"oops"}}""")

        assertNull(envelope.error.retryable)
        assertNull(envelope.error.dataset)
    }
}
