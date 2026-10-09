package uk.gov.defra.mmocatchrecord.core.error

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.DraftMappingException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.ReferenceDataHttpException
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
    fun `an unrecognised throwable classifies as Terminal`() {
        assertEquals(CatchRecordErrorClassification.Terminal, SafeErrorMapper.classify(IllegalStateException()))
    }

    @Test
    fun `mapped messages never leak the throwable's own message`() {
        val error = IOException("super secret internal detail")

        val mapped = SafeErrorMapper.map(error)

        assertFalse(mapped.message.contains("secret"))
    }
}
