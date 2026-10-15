@file:Suppress("detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip

import kotlinx.coroutines.Dispatchers
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.ReferenceDataRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.StatisticalSubRectangle
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Vessel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewModel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeCatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeCatchRecordSubmissionRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeCatchRecordSyncScheduler
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeNetworkConnectivityChecker
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeReferenceDataRepository

/** A seeded, in-progress draft shared by the trip-screen ViewModel-driven presentation tests below. */
fun tripScreenSeedDraft(
    id: String = "draft-1",
    vesselId: String = "vessel-achilles",
): CatchRecordDraft =
    CatchRecordDraft(
        id = id,
        vesselId = vesselId,
        status = DraftStatus.Draft,
        modifiedAtEpochMillis = 0L,
    )

/** Builds a real [CatchRecordFlowViewModel] wired to in-memory fakes, mirroring `CatchRecordFlowViewModelTests`. */
@Suppress("LongParameterList")
fun buildTripFlowViewModel(
    draftRepository: CatchRecordDraftRepository = FakeCatchRecordDraftRepository(),
    referenceDataRepository: ReferenceDataRepository = FakeReferenceDataRepository(),
    submissionRepository: FakeCatchRecordSubmissionRepository = FakeCatchRecordSubmissionRepository(),
    connectivityChecker: FakeNetworkConnectivityChecker = FakeNetworkConnectivityChecker(),
    syncScheduler: FakeCatchRecordSyncScheduler = FakeCatchRecordSyncScheduler(),
    clock: () -> Long = { 0L },
    idFactory: () -> String = { "generated-id" },
): CatchRecordFlowViewModel =
    CatchRecordFlowViewModel(
        draftRepository,
        referenceDataRepository,
        submissionRepository,
        connectivityChecker,
        syncScheduler,
        clock,
        idFactory,
        Dispatchers.Default,
    )

/** Failing reference data lets tests choose retryable or terminal error branches by exception type. */
class FailingReferenceDataRepository(
    private val failure: Throwable,
) : ReferenceDataRepository {
    override suspend fun getVessels(): Result<List<Vessel>> = Result.failure(failure)

    override suspend fun getPorts(): Result<List<Port>> = Result.success(emptyList())

    override suspend fun getPreviouslyUsedPorts(vesselId: String): Result<List<Port>> = Result.success(emptyList())

    override suspend fun getGearTypes(vesselLengthMetres: Double?): Result<List<GearType>> = Result.success(emptyList())

    override suspend fun getSpecies(): Result<List<Species>> = Result.success(emptyList())

    override suspend fun getStatisticalSubRectangles(): Result<List<StatisticalSubRectangle>> =
        Result.success(emptyList())

    override suspend fun getStatisticalSubRectanglesForPort(portId: String): Result<List<StatisticalSubRectangle>> =
        Result.success(emptyList())
}
