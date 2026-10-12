package uk.gov.defra.mmocatchrecord.feature.home.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import uk.gov.defra.mmocatchrecord.feature.home.domain.HomeRepository
import uk.gov.defra.mmocatchrecord.feature.home.domain.HomeSummary

/** Test-only [HomeRepository] fake (the `main` one was deleted — see ADR 0014 Phase B). */
class FakeHomeRepository : HomeRepository {
    private val results = MutableSharedFlow<Result<HomeSummary>>(replay = 1)

    override fun observeSummary(): Flow<HomeSummary> = results.map { it.getOrThrow() }

    fun emit(summary: HomeSummary) {
        results.tryEmit(Result.success(summary))
    }

    fun emitError(error: Throwable) {
        results.tryEmit(Result.failure(error))
    }
}
