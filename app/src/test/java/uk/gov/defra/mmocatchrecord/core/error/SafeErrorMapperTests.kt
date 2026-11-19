package uk.gov.defra.mmocatchrecord.core.error

import android.database.SQLException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.DraftMappingException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.ReferenceDataHttpException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.CatchRecordHttpException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.CatchRecordUnauthorizedException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MissingVesselRegistrationException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SubmissionMappingException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.UnresolvedGearMeasurementException
import java.io.IOException

/** [SafeErrorMapper] never surfaces a raw message and classifies every known failure kind (ADR 0011/0015). */
class SafeErrorMapperTests {
    @Test
    fun `a retryable reference-data failure classifies as Transient and is retryable`() {
        val error = ReferenceDataHttpException("vessels", 503, "reference_data_unavailable", retryable = true)

        assertEquals(CatchRecordErrorClassification.Transient, SafeErrorMapper.classify(error))
        assertTrue(SafeErrorMapper.map(error).isRetryable)
    }

    @Test
    fun `a non-retryable reference-data failure classifies as Terminal and is not retryable`() {
        val error = ReferenceDataHttpException("vessels", 400, "invalid_request", retryable = false)

        assertEquals(CatchRecordErrorClassification.Terminal, SafeErrorMapper.classify(error))
        assertFalse(SafeErrorMapper.map(error).isRetryable)
    }

    @Test
    fun `a draft mapping failure classifies as Terminal`() {
        assertEquals(
            CatchRecordErrorClassification.Terminal,
            SafeErrorMapper.classify(DraftMappingException("corrupt draft")),
        )
    }

    @Test
    fun `a generic IOException classifies as Transient`() {
        assertEquals(CatchRecordErrorClassification.Transient, SafeErrorMapper.classify(IOException("boom")))
    }

    @Test
    fun `a submission mapping failure classifies as Terminal`() {
        assertEquals(
            CatchRecordErrorClassification.Terminal,
            SafeErrorMapper.classify(SubmissionMappingException("species-1 unresolvable")),
        )
    }

    @Test
    fun `an unresolved gear measurement classifies as Transient, not Terminal, and hides the field id`() {
        val error = UnresolvedGearMeasurementException("measurement gear-1:mesh_size_mm has no apiMeasurementId")

        assertEquals(CatchRecordErrorClassification.Transient, SafeErrorMapper.classify(error))
        assertFalse(SafeErrorMapper.map(error).message.contains("mesh_size_mm"))
    }

    @Test
    fun `an unrecognised throwable classifies as Terminal`() {
        assertEquals(CatchRecordErrorClassification.Terminal, SafeErrorMapper.classify(IllegalStateException()))
    }

    @Test
    fun `mapped messages never leak the throwable's own message`() {
        val error = IOException("super secret internal detail")

        val mapped = SafeErrorMapper.map(error)

        assertFalse(mapped.message.contains("secret"))
    }

    @Test
    fun `a vessel with no registration maps to the specific non-retryable message`() {
        val mapped = SafeErrorMapper.map(MissingVesselRegistrationException())

        assertEquals(
            "You cannot start a catch record for this vessel because its registration details are missing. " +
                "Contact the MMO.",
            mapped.message,
        )
        assertFalse(mapped.isRetryable)
    }

    @Test
    fun `mapSubmitLocalSave maps a missing vessel registration to the specific non-retryable message`() {
        val mapped = SafeErrorMapper.mapSubmitLocalSave(MissingVesselRegistrationException())

        assertEquals(
            "You cannot start a catch record for this vessel because its registration details are missing. " +
                "Contact the MMO.",
            mapped.message,
        )
        assertFalse(mapped.isRetryable)
    }

    @Test
    fun `mapSubmitLocalSave maps a terminal failure to the shared, non-retryable terminal message`() {
        val mapped = SafeErrorMapper.mapSubmitLocalSave(DraftMappingException("corrupt draft"))

        assertEquals("Something went wrong and we could not complete that action.", mapped.message)
        assertFalse(mapped.isRetryable)
    }

    @Test
    fun `catch-record api failures classify by their own retryable flag`() {
        val retryable = CatchRecordHttpException("submit", 503, null, null, retryable = true)
        val terminal = CatchRecordHttpException("submit", 400, "INVALID", null, retryable = false)

        assertEquals(CatchRecordErrorClassification.Transient, SafeErrorMapper.classify(retryable))
        assertEquals(CatchRecordErrorClassification.Terminal, SafeErrorMapper.classify(terminal))
    }

    @Test
    fun `an unauthorized catch-record failure needs authentication and maps to a non-retryable message`() {
        val error = CatchRecordUnauthorizedException("submit", correlationId = null)

        assertEquals(CatchRecordErrorClassification.NeedsAuthentication, SafeErrorMapper.classify(error))
        assertFalse(SafeErrorMapper.map(error).isRetryable)
        assertFalse(SafeErrorMapper.mapSubmitLocalSave(error).isRetryable)
    }

    @Test
    fun `a local SQL failure classifies as Transient`() {
        assertEquals(CatchRecordErrorClassification.Transient, SafeErrorMapper.classify(SQLException("disk")))
    }

    @Test
    fun `mapSubmitLocalSave maps a transient failure to the submit-specific retryable message`() {
        val mapped = SafeErrorMapper.mapSubmitLocalSave(IOException("disk"))

        assertEquals("We could not save your catch record on this device. Try again.", mapped.message)
        assertTrue(mapped.isRetryable)
    }
}
