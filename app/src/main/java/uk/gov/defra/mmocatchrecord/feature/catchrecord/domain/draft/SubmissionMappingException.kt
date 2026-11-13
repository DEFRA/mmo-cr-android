package uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft

/**
 * A regulatory field could not be resolved against cached reference data at submission-mapping time
 * (ADR 0022) — always terminal; retrying cannot fix missing/unresolvable reference data.
 */
class SubmissionMappingException(
    message: String,
) : IllegalStateException(message)
