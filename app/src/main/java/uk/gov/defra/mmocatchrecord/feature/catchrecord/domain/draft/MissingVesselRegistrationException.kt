package uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft

/** The selected vessel has no PLN/RSS number, so no Trip ID can be generated and no record started. */
class MissingVesselRegistrationException : IllegalStateException("Vessel has no registration number")
