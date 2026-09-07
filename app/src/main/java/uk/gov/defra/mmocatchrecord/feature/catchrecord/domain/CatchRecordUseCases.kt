package uk.gov.defra.mmocatchrecord.feature.catchrecord.domain

import uk.gov.defra.mmocatchrecord.core.architecture.ValidationHelper
import uk.gov.defra.mmocatchrecord.core.architecture.ValidationResult
import javax.inject.Inject

/** Use-case validating and persisting a new [CatchRecord] via [CatchRecordRepository]. */
class SaveCatchRecordUseCase
    @Inject
    constructor(
        private val repository: CatchRecordRepository,
        private val clock: () -> Long = { System.currentTimeMillis() },
    ) {
    suspend operator fun invoke(
        id: String,
        species: String,
        weightKg: Double,
    ): Result<Unit> {
        val speciesValidation = ValidationHelper.requireNotBlank(species, "Species")
        val weightValidation = ValidationHelper.requirePositive(weightKg, "Weight")

        val firstInvalid =
            listOf(speciesValidation, weightValidation)
                .filterIsInstance<ValidationResult.Invalid>()
                .firstOrNull()

        if (firstInvalid != null) {
            return Result.failure(IllegalArgumentException(firstInvalid.reason))
        }

        return repository.saveCatchRecord(
            CatchRecord(
                id = id,
                species = species,
                weightKg = weightKg,
                recordedAtEpochMillis = clock(),
            ),
        )
    }
}

/** Use-case listing all previously recorded catches, most recent first. */
class GetCatchRecordsUseCase
    @Inject
    constructor(
        private val repository: CatchRecordRepository,
    ) {
    suspend operator fun invoke(): Result<List<CatchRecord>> =
        repository.getCatchRecords().map { records ->
            records.sortedByDescending { it.recordedAtEpochMillis }
        }
}
