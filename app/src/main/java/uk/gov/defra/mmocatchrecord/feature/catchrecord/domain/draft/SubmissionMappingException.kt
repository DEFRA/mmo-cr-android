package uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft

/** A regulatory field was unresolvable at submission-mapping time (ADR 0026) — always terminal. */
open class SubmissionMappingException(
    message: String,
) : IllegalStateException(message)

/** A gear measurement's `characteristicId` was blank — likely stale cached data (ADR 0026). */
class UnresolvedGearMeasurementException(
    message: String,
) : SubmissionMappingException(message)
